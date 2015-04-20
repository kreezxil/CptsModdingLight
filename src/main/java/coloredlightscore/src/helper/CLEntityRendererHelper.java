package coloredlightscore.src.helper;

import static org.lwjgl.opengl.GL11.*;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.potion.Potion;
import org.lwjgl.opengl.GL11;

public class CLEntityRendererHelper {

    public static final float f = (1.0F/4096.0F);
    public static final float t = 8.0f;
    public static final float nightVisionMinBrightness = 0.7f;
    private static boolean ignoreNextEnableLightmap;
    private static float[] trueBlackLightBrightnessTable;

    // Begin programmable lighting engine parameters

    public static float minLightLevel = 0.05f;
    public static float maxLightLevel = 1.0f;

    // End light engine params

    public static void Initialize() {

        // This table allows us to achieve true blackness at the low-end of the color spectrum.
        // The vanilla brightness tables bottom out too bright, preventing mods such as
        // "Hardcore Darkness" from working properly with this mod.

        trueBlackLightBrightnessTable = new float[]
                {
                        0.0f,
                        0.066f,
                        0.132f,
                        0.198f,
                        0.264f,
                        0.330f,
                        0.396f,
                        0.462f,
                        0.528f,
                        0.594f,
                        0.660f,
                        0.726f,
                        0.792f,
                        0.858f,
                        0.924f,
                        1.0f
                };
    }
    
    public static void updateLightmap(EntityRenderer instance, float partialTickTime) {
        WorldClient worldclient = instance.mc.theWorld;
        
        float min = minLightLevel;
        float max = maxLightLevel;

        if (instance.mc.thePlayer.isPotionActive(Potion.nightVision)) {
            float nightVisionWeight = instance.getNightVisionBrightness(instance.mc.thePlayer, partialTickTime);
            min = min * (1.0f - nightVisionWeight) + nightVisionMinBrightness * nightVisionWeight;
        }
        
        if (worldclient != null) {
            int[] map = new int[16*16*16*16];
            float sunlightBase = worldclient.getSunBrightness(partialTickTime);
            float sunlight, bSunlight, gSunlight, rSunlight, bLight, gLight, rLight, gamma;

            gamma = instance.mc.gameSettings.gammaSetting;
            for (int s = 0; s < 16; s++) {
                sunlight = sunlightBase * trueBlackLightBrightnessTable[s];
                if (worldclient.lastLightningBolt > 0) {
                    sunlight = trueBlackLightBrightnessTable[s];
                }

                rSunlight = sunlight * Math.min(1, worldclient.clSunColor[0] - worldclient.clMoonColor[0]) + worldclient.clMoonColor[0];
                gSunlight = sunlight * Math.min(1, worldclient.clSunColor[1] - worldclient.clMoonColor[1]) + worldclient.clMoonColor[1];
                bSunlight = sunlight * Math.min(1, worldclient.clSunColor[2] - worldclient.clMoonColor[2]) + worldclient.clMoonColor[2];


                for (int b = 0; b < 16; b++) {
                    bLight = trueBlackLightBrightnessTable[b] + bSunlight;
                    bLight = applyGamma(bLight, gamma) * (max - min) + min;

                    for (int g = 0; g < 16; g++) {
                        gLight = trueBlackLightBrightnessTable[g] + gSunlight;
                        gLight = applyGamma(gLight, gamma) * (max - min) + min;

                        for (int r = 0; r < 16; r++) {
                            rLight = trueBlackLightBrightnessTable[r] + rSunlight;
                            rLight = applyGamma(rLight, gamma) * (max - min) + min;

                            map[g << 12 | s << 8 | r << 4 | b] = 255 << 24 | (int) (rLight * 255) << 16 | (int) (gLight * 255) << 8 | (int) (bLight * 255);
                        }
                    }
                }
            }
            instance.setLightmapTexture(map);
            
            instance.lightmapTexture.updateDynamicTexture();
            instance.lightmapUpdateNeeded = false;
        }
    }

    private static float applyGamma(float light, float gamma) {
        float lightC;
        light = clamp(light, 0.0f, 1.0f);
        lightC = 1 - light;
        light = light * (1 - gamma) + (1 - lightC * lightC * lightC * lightC) * gamma;
        light = 0.96f * light + 0.03f;
        light = clamp(light, 0.0f, 1.0f);
        return light;
    }

    private static float clamp(float x, float lower, float upper) {
        if (lower > upper) {
            throw new IllegalArgumentException("Lower bound cannot be greater than upper bound!");
        }
        if (x < lower) {
            x = lower;
        }
        if (x > upper) {
            x = upper;
        }
        return x;
    }

    public static void enableLightmap(EntityRenderer instance, double par1) {
        if (ignoreNextEnableLightmap) {
            ignoreNextEnableLightmap = false;
            return;
        }
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GL11.glScalef(f, f, f);
        GL11.glTranslatef(t, t, t);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        instance.mc.getTextureManager().bindTexture(instance.locationLightMap);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        CLTessellatorHelper.enableShader();
    }

    public static void disableLightmap(EntityRenderer instance, double par1) {
        CLTessellatorHelper.disableShader();
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        glDisable(GL_TEXTURE_2D);
        
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    public static void disableLightmap(EntityRenderer instance, double par1, boolean forRealz) {
        if (!forRealz) {
            ignoreNextEnableLightmap = true;
            return;
        }
        disableLightmap(instance, par1);
    }
}
