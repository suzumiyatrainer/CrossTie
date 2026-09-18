package net.suzumiya.crosstie.utils.texture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GifDecoderUtils {

    public static void loadGif(GifTextureRecord record) {
        ResourceLocation location = record.getLocation();
        if (location == null) return;

        InputStream is = null;
        try {
            is = openResourceStream(location);
            if (is == null) {
                System.err.println("[CrossTie-GIF] Could not open resource stream for: " + location);
                record.setFrames(new int[0], new int[0], 0);
                return;
            }

            try (ImageInputStream iis = ImageIO.createImageInputStream(is)) {
                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
                if (!readers.hasNext()) {
                    System.err.println("[CrossTie-GIF] No GIF ImageReader available");
                    record.setFrames(new int[0], new int[0], 0);
                    return;
                }

                ImageReader reader = readers.next();
                reader.setInput(iis, false);

                int numFrames = reader.getNumImages(true);
                if (numFrames <= 0) {
                    reader.dispose();
                    record.setFrames(new int[0], new int[0], 0);
                    return;
                }

                int width = -1;
                int height = -1;

                List<Integer> texIdList = new ArrayList<>();
                List<Integer> delayList = new ArrayList<>();
                int totalDuration = 0;

                BufferedImage masterImage = null;
                Graphics2D masterGraphics = null;

                for (int i = 0; i < numFrames; i++) {
                    BufferedImage frame = reader.read(i);
                    IIOMetadata metadata = reader.getImageMetadata(i);

                    if (width == -1 || height == -1) {
                        width = reader.getWidth(0);
                        height = reader.getHeight(0);
                        masterImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                        masterGraphics = masterImage.createGraphics();
                    }

                    int x = 0;
                    int y = 0;
                    int delay = 100;
                    String disposal = "none";

                    if (metadata != null) {
                        String metaFormatName = metadata.getNativeMetadataFormatName();
                        if (metaFormatName != null) {
                            Node root = metadata.getAsTree(metaFormatName);
                            NodeList children = root.getChildNodes();
                            for (int c = 0; c < children.getLength(); c++) {
                                Node nodeItem = children.item(c);
                                String nodeName = nodeItem.getNodeName();
                                if ("GraphicControlExtension".equalsIgnoreCase(nodeName)) {
                                    NamedNodeMap attrs = nodeItem.getAttributes();
                                    if (attrs != null) {
                                        Node delayNode = attrs.getNamedItem("delayTime");
                                        if (delayNode != null) {
                                            try {
                                                delay = Integer.parseInt(delayNode.getNodeValue()) * 10;
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                        Node dispNode = attrs.getNamedItem("disposalMethod");
                                        if (dispNode != null) {
                                            disposal = dispNode.getNodeValue();
                                        }
                                    }
                                } else if ("ImageDescriptor".equalsIgnoreCase(nodeName)) {
                                    NamedNodeMap attrs = nodeItem.getAttributes();
                                    if (attrs != null) {
                                        Node leftNode = attrs.getNamedItem("imageLeftPosition");
                                        if (leftNode != null) {
                                            try {
                                                x = Integer.parseInt(leftNode.getNodeValue());
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                        Node topNode = attrs.getNamedItem("imageTopPosition");
                                        if (topNode != null) {
                                            try {
                                                y = Integer.parseInt(topNode.getNodeValue());
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (delay <= 0) {
                        delay = 100; // default 100ms
                    }

                    if (masterGraphics != null) {
                        masterGraphics.drawImage(frame, x, y, null);
                    }

                    // Create copy of current combined state for GL upload
                    BufferedImage glImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = glImage.createGraphics();
                    g.drawImage(masterImage, 0, 0, null);
                    g.dispose();

                    // If restoreToBackgroundColor / restoreToPrevious, handle accordingly
                    if ("restoreToBackgroundColor".equalsIgnoreCase(disposal) && masterGraphics != null) {
                        masterGraphics.clearRect(x, y, frame.getWidth(), frame.getHeight());
                    }

                    int glTextureId = org.lwjgl.opengl.GL11.glGenTextures();
                    TextureUtil.uploadTextureImage(glTextureId, glImage);

                    texIdList.add(glTextureId);
                    delayList.add(delay);
                    totalDuration += delay;
                }

                if (masterGraphics != null) {
                    masterGraphics.dispose();
                }
                reader.dispose();

                int[] textureIds = new int[texIdList.size()];
                int[] delayTimes = new int[delayList.size()];
                for (int j = 0; j < texIdList.size(); j++) {
                    textureIds[j] = texIdList.get(j);
                    delayTimes[j] = delayList.get(j);
                }

                record.setFrames(textureIds, delayTimes, totalDuration);
                System.out.println("[CrossTie-GIF] Successfully loaded " + textureIds.length + " frames for " + location + " (Total duration: " + totalDuration + "ms)");
            }
        } catch (Exception e) {
            System.err.println("[CrossTie-GIF] Failed to decode GIF: " + location);
            e.printStackTrace();
            record.setFrames(new int[0], new int[0], 0);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static InputStream openResourceStream(ResourceLocation loc) {
        // 1. Try standard resource manager with current location
        try {
            return Minecraft.getMinecraft().getResourceManager().getResource(loc).getInputStream();
        } catch (Exception ignored) {
        }

        // 2. Try with "rtm" domain if domain was "minecraft"
        if ("minecraft".equals(loc.getResourceDomain())) {
            try {
                ResourceLocation rtmLoc = new ResourceLocation("rtm", loc.getResourcePath());
                return Minecraft.getMinecraft().getResourceManager().getResource(rtmLoc).getInputStream();
            } catch (Exception ignored) {
            }
        }

        // 3. Try with "minecraft" domain if domain was "rtm"
        if ("rtm".equals(loc.getResourceDomain())) {
            try {
                ResourceLocation mcLoc = new ResourceLocation("minecraft", loc.getResourcePath());
                return Minecraft.getMinecraft().getResourceManager().getResource(mcLoc).getInputStream();
            } catch (Exception ignored) {
            }
        }

        // 4. Fallback to ClassLoader resource
        try {
            String path = loc.getResourcePath();
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            InputStream clIs = GifDecoderUtils.class.getResourceAsStream(path);
            if (clIs != null) {
                return clIs;
            }
            clIs = GifDecoderUtils.class.getResourceAsStream("/assets/" + loc.getResourceDomain() + "/" + loc.getResourcePath());
            if (clIs != null) {
                return clIs;
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
