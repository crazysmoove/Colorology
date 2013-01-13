import ar.com.hjg.pngj.FileHelper
import ar.com.hjg.pngj.ImageLine
import ar.com.hjg.pngj.ImageLineHelper
import ar.com.hjg.pngj.PngReader
import ar.com.hjg.pngj.PngWriter
import ar.com.hjg.pngj.chunks.ChunkCopyBehaviour
import ar.com.hjg.pngj.chunks.PngChunkPLTE
import groovy.transform.Canonical

// Given a source image and a new "base color," create a new image that is
// the analog of the original image but in the new color.  For example:
// - Given a PNG of a logo that is in shades of green, and
// - Given a new "base color" of orange,
// - Create a new file that is the same logo in various shades of orange

def inputFilename = "c:/projects/colors/green_A.png"
//def inputFilename = "c:/projects/colors/logo_blue.png"
def outputFilename = "c:/projects/colors/out-test.png"
def oldBaseColor = "#8FC051"
//def oldBaseColor = "#5C8DD6"
def newBaseColor = "#FF7F00"
//def newBaseColor = "#FF0000"

println "Old base color (hex, rgb): ${oldBaseColor}"
println "New base color (hex, rgb): ${newBaseColor}"

def oldBaseHsl = hexRgbToHsl(oldBaseColor)
def newBaseHsl = hexRgbToHsl(newBaseColor)

println "Old base color (hsl): ${oldBaseHsl}"
println "New base color (hsl): ${newBaseHsl}"

// from: https://code.google.com/p/pngj/wiki/Overview
PngReader reader = FileHelper.createPngReader(new File(inputFilename))
PngWriter writer = FileHelper.createPngWriter(new File(outputFilename), reader.imgInfo, true)

writer.copyChunksFirst(reader, ChunkCopyBehaviour.COPY_ALL_SAFE)

int channels = reader.imgInfo.channels

if (channels < 3) {
    throw new RuntimeException("This method is for RGB/RGBA images")
}

for (int i = 0; i < reader.imgInfo.rows; i++) {
    ImageLine line = reader.readRow(i)
    for (int j = 0; j < reader.imgInfo.cols; j++) {
        //line.scanline[j * (channels - 1)] /= 2
        int packedRgb = ImageLineHelper.getPixelRGB8(line, j)
        int r = (packedRgb >> 16) & 0xff
        int g = (packedRgb >> 8) & 0xff
        int b = packedRgb & 0xff
        String hexStr = "#" + hexStr(r) + hexStr(g) + hexStr(b)
        def thisPixelHslColor = hexRgbToHsl(hexStr)
        def newHue = newBaseHsl.hueDegrees
        // calculate diff between old base and this pixel, apply that diff to new base to get new pixel value:
        def oldSaturationDiff = thisPixelHslColor.saturationPercent - oldBaseHsl.saturationPercent
        def newPixelSaturation = newBaseHsl.saturationPercent + oldSaturationDiff
        def oldLightnessDiff = thisPixelHslColor.lightnessPercent - oldBaseHsl.lightnessPercent
        def newPixelLightness = newBaseHsl.lightnessPercent + oldLightnessDiff
        def newPixelHslColor = null
        //if (thisPixelHslColor.hueDegrees == oldBaseHsl.hueDegrees) {
        def oldHueDiff = thisPixelHslColor.hueDegrees - oldBaseHsl.hueDegrees
        def newPixelHue = newBaseHsl.hueDegrees + oldHueDiff
        if (isHueClose(thisPixelHslColor, oldBaseHsl)) {
            newPixelHslColor = new HslColor(hueDegrees: newPixelHue /*newHue*/,
                                            saturationPercent: newPixelSaturation,
                                            lightnessPercent: newPixelLightness)
        }
        if (newPixelHslColor == null) {
            // don't change it
        } else {
            //line.scanline[j * channels] /= 2
            def newPixelRgbColor = hsl2rgb(newPixelHslColor)
            //print "${newPixelRgbColor} "
            ImageLineHelper.setPixelRGB8(line, j, newPixelRgbColor.red, newPixelRgbColor.green, newPixelRgbColor.blue)
        }
        //print "${newPixelHslColor} "
    }
    //println ""
    writer.writeRow(line, i)
}

writer.copyChunksLast(reader, ChunkCopyBehaviour.COPY_ALL_SAFE)

writer.end()
reader.end()

// methods ////////////////////////////////////////////////////////////////////////////////////////////////////////////

def isHueClose(HslColor thisPixelHslColor, HslColor oldBaseHsl) {
    return thisPixelHslColor.hueDegrees >= oldBaseHsl.hueDegrees - 10 &&
            thisPixelHslColor.hueDegrees <= oldBaseHsl.hueDegrees + 10
}

def hexStr(int i) {
    String str = Integer.toHexString(i)
    if (str.length() == 1) {
        return "0" + str
    } else {
        return str
    }
}

// from pseudocode at: http://www.easyrgb.com/index.php?X=MATH&H=18#text18
def hexRgbToHsl(String hexRgbColor) {

    hexRgbColor = hexRgbColor.replaceFirst("#", "")

    def hexRed = hexRgbColor.substring(0, 2)
    def hexGreen = hexRgbColor.substring(2, 4)
    def hexBlue = hexRgbColor.substring(4, 6)

    def decRed = Integer.parseInt(hexRed, 16)
    def decGreen = Integer.parseInt(hexGreen, 16)
    def decBlue = Integer.parseInt(hexBlue, 16)

    def r = decRed / 255
    def g = decGreen / 255
    def b = decBlue / 255

    def min = Math.min(r, Math.min(g, b))
    def max = Math.max(r, Math.max(g, b))
    def delta = max - min

    def lightness = (max + min) / 2

    def hue, saturation

    if (delta == 0) {

        hue = saturation = 0

    } else {

        if (lightness < 0.5) {
            saturation = delta / (max + min)
        } else {
            saturation = delta / (2 - max - min)
        }

        def deltaR = ( ( (max - r) / 6 ) + (delta / 2) ) / delta
        def deltaG = ( ( (max - g) / 6 ) + (delta / 2) ) / delta
        def deltaB = ( ( (max - b) / 6 ) + (delta / 2) ) / delta

        if (r == max) {
            hue = deltaB - deltaG
        } else if (g == max) {
            hue = (1 / 3) + deltaR - deltaB
        } else if (b == max) {
            hue = (2 / 3) + deltaG - deltaR
        }

        if (hue < 0) {
            hue = hue + 1
        }

        if (hue > 1) {
            hue = hue - 1
        }

    }

    return new HslColor(hueDegrees: hue * 360,
                        saturationPercent: saturation * 100,
                        lightnessPercent: lightness * 100)
    //return new HslColor(hueDegrees: hue, saturationPercent: saturation, lightnessPercent: lightness)

}

def hsl2rgb(HslColor hslColor) {

    hslColor = new HslColor(hueDegrees: hslColor.hueDegrees / 360,
                            saturationPercent: hslColor.saturationPercent / 100,
                            lightnessPercent: hslColor.lightnessPercent / 100)

    if (hslColor.saturationPercent == 0) {
        return new RgbColor(red: hslColor.lightnessPercent * 255,
                            green: hslColor.lightnessPercent * 255,
                            blue: hslColor.lightnessPercent * 255)
    } else {
        def var1, var2
        if (hslColor.lightnessPercent < 0.5) {
            var2 = hslColor.lightnessPercent * (1 + hslColor.saturationPercent)
        } else {
            var2 = (hslColor.lightnessPercent + hslColor.saturationPercent) -
                   (hslColor.saturationPercent * hslColor.lightnessPercent)
        }
        var1 = 2 * hslColor.lightnessPercent - var2
        def r = 255 * hue2rgb(var1, var2, hslColor.hueDegrees + (1 / 3))
        def g = 255 * hue2rgb(var1, var2, hslColor.hueDegrees)
        def b = 255 * hue2rgb(var1, var2, hslColor.hueDegrees - (1 / 3))
        return new RgbColor(red: r, green: g, blue: b)
    }

}

def hue2rgb(v1, v2, vH) {
    if ( vH < 0 ) {
        vH += 1
    }
    if ( vH > 1 ) {
        vH -= 1
    }
    if ( ( 6 * vH ) < 1 ) {
        return ( v1 + ( v2 - v1 ) * 6 * vH )
    }
    if ( ( 2 * vH ) < 1 ) {
        return ( v2 )
    }
    if ( ( 3 * vH ) < 2 ) {
        return ( v1 + ( v2 - v1 ) * ( ( 2 / 3 ) - vH ) * 6 )
    }
    return ( v1 )
}

// classes ////////////////////////////////////////////////////////////////////////////////////////////////////////////

@Canonical
class RgbColor {
    int red, green, blue
}

@Canonical
class HslColor {
    double hueDegrees
    double saturationPercent
    double lightnessPercent
    def setHueDegrees(deg) {
        if (deg < 0) {
            hueDegrees = 0
        } else if (deg > 359) {
            hueDegrees = 359
        } else {
            hueDegrees = deg
        }
    }
    def setSaturationPercent(pct) {
        if (pct < 0) {
            saturationPercent = 0
        } else if (pct > 100) {
            saturationPercent = 100
        } else {
            saturationPercent = pct
        }
    }
    def setLightnessPercent(pct) {
        if (pct < 0) {
            lightnessPercent = 0
        } else if (pct > 100) {
            lightnessPercent = 100
        } else {
            lightnessPercent = pct
        }
    }
}