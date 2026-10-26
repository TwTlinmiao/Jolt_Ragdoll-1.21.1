package com.ragdoll.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Builds a textured shell with thickness along the transparent edges of HD skins. */
final class HdSkinLayerRenderer {
    private static final Cache<Key, Geometry> CACHE = CacheBuilder.newBuilder()
            .maximumSize(192).expireAfterWrite(60, TimeUnit.SECONDS).build();
    private static boolean lookupInitialized;
    private static Method skinTextureLookup;

    private HdSkinLayerRenderer() {}

    static boolean render(ResourceLocation texture, String part, boolean slim,
                          ModelPart outer, float x, float y,
                          PoseStack pose, VertexConsumer buffer, int light) {
        AbstractTexture imageTexture = Minecraft.getInstance().getTextureManager().getTexture(texture);
        Key key = new Key(texture, imageTexture, part, slim);
        Geometry geometry = CACHE.getIfPresent(key);
        long now = System.nanoTime();
        if (geometry == null || now >= geometry.retryAt()) {
            geometry = load(texture, imageTexture, outer);
            CACHE.put(key, geometry);
        }
        if (geometry.vertices() == null) {
            return false;
        }
        pose.pushPose();
        try {
            pose.translate(x / 16f, y / 16f, 0);
            for (Quad quad : geometry.vertices()) {
                Vector3f normal = pose.last().transformNormal(quad.normal(), new Vector3f());
                for (Point point : quad.points()) {
                    Vector3f position = pose.last().pose().transformPosition(point.position(), new Vector3f());
                    buffer.addVertex(position.x, position.y, position.z, -1, point.u(), point.v(),
                            OverlayTexture.NO_OVERLAY, light, normal.x, normal.y, normal.z);
                }
            }
        } finally {
            pose.popPose();
        }
        return true;
    }

    private static Geometry load(ResourceLocation texture, AbstractTexture imageTexture, ModelPart outer) {
        try {
            if (imageTexture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
                return build(dynamic.getPixels(), outer);
            }
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isPresent()) {
                try (var stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                    return build(image, outer);
                }
            }
            if (!lookupInitialized) {
                lookupInitialized = true;
                try {
                    skinTextureLookup = Class.forName("dev.tr7zw.skinlayers.SkinUtil")
                            .getMethod("getTexture", ResourceLocation.class,
                                    Class.forName("dev.tr7zw.skinlayers.accessor.SkullSettings"));
                } catch (ReflectiveOperationException ignored) {
                }
            }
            if (skinTextureLookup != null) {
                // Skin Layers owns this image; do not close it.
                Object image = skinTextureLookup.invoke(null, texture, null);
                if (image instanceof NativeImage nativeImage) {
                    return build(nativeImage, outer);
                }
            }
            // Some HD skin providers retain only the GPU texture. Query its
            // actual dimensions before allocating a readback image.
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                GlStateManager._bindTexture(imageTexture.getId());
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                if (width > 64 && width <= 8192 && width == height && width % 64 == 0) {
                    try (NativeImage image = new NativeImage(width, height, false)) {
                        image.downloadTexture(0, false);
                        return build(image, outer);
                    }
                }
            } finally {
                GlStateManager._bindTexture(previousTexture);
            }
        } catch (Exception ignored) {
            // A skin may still be downloading; retry rather than caching failure forever.
        }
        return new Geometry(null, System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
    }

    private static Geometry build(NativeImage image, ModelPart outer) {
        int size = image.getWidth();
        if (size <= 64 || size != image.getHeight() || size % 64 != 0) {
            return new Geometry(null, System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
        }
        List<Quad> quads = new ArrayList<>();
        Collector collector = new Collector();
        // Compile the vanilla cube without its pose. This preserves all six UV
        // orientations, including the bottom face and slim arm offsets.
        outer.visit(new PoseStack(), (ignored, path, index, cube) ->
                cube.compile(new PoseStack().last(), collector, 0, 0, -1));
        for (int face = 0; face < collector.points.size(); face += 4) {
            Point a = collector.points.get(face);
            Point b = collector.points.get(face + 1);
            Point c = collector.points.get(face + 2);
            Point d = collector.points.get(face + 3);
            Vector3f normal = collector.normals.get(face);
            int width = Math.round(Math.abs(b.u() - a.u()) * size);
            int height = Math.round(Math.abs(d.v() - a.v()) * size);
            boolean[][] filled = new boolean[height][width];
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    Point p = interpolate(a, b, c, d, (col + .5f) / width, (row + .5f) / height);
                    int px = Math.clamp((int) (p.u() * size), 0, size - 1);
                    int py = Math.clamp((int) (p.v() * size), 0, size - 1);
                    filled[row][col] = (image.getPixelRGBA(px, py) >>> 24) != 0;
                }
            }
            // One textured front face keeps the original HD detail and alpha.
            // Only exposed pixel edges need extra geometry for the thickness.
            quads.add(new Quad(new Point[]{a, b, c, d}, normal));
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    if (!filled[row][col]) continue;
                    Point[] corners = {
                            interpolate(a, b, c, d, (float) col / width, (float) row / height),
                            interpolate(a, b, c, d, (col + 1f) / width, (float) row / height),
                            interpolate(a, b, c, d, (col + 1f) / width, (row + 1f) / height),
                            interpolate(a, b, c, d, (float) col / width, (row + 1f) / height)};
                    boolean[] edges = {row == 0 || !filled[row - 1][col],
                            col == width - 1 || !filled[row][col + 1],
                            row == height - 1 || !filled[row + 1][col],
                            col == 0 || !filled[row][col - 1]};
                    float u = (corners[0].u() + corners[2].u()) / 2;
                    float v = (corners[0].v() + corners[2].v()) / 2;
                    for (int edge = 0; edge < 4; edge++) {
                        if (!edges[edge]) continue;
                        Vector3f p = corners[edge].position();
                        Vector3f q = corners[(edge + 1) % 4].position();
                        Vector3f inside = new Vector3f(normal).mul(-0.5f / 16f);
                        Vector3f sideNormal = new Vector3f(q).sub(p).cross(normal).normalize();
                        quads.add(new Quad(new Point[]{new Point(q, u, v), new Point(p, u, v),
                                new Point(new Vector3f(p).add(inside), u, v),
                                new Point(new Vector3f(q).add(inside), u, v)}, sideNormal));
                    }
                }
            }
        }
        return new Geometry(List.copyOf(quads), Long.MAX_VALUE);
    }

    private static Point interpolate(Point a, Point b, Point c, Point d, float s, float t) {
        Vector3f position = new Vector3f(a.position()).lerp(b.position(), s)
                .lerp(new Vector3f(d.position()).lerp(c.position(), s), t);
        return new Point(position, a.u() + (b.u() - a.u()) * s,
                a.v() + (d.v() - a.v()) * t);
    }

    private record Key(ResourceLocation texture, AbstractTexture image, String part, boolean slim) {}
    private record Geometry(List<Quad> vertices, long retryAt) {}
    private record Point(Vector3f position, float u, float v) {}
    private record Quad(Point[] points, Vector3f normal) {}

    private static final class Collector implements VertexConsumer {
        final List<Point> points = new ArrayList<>();
        final List<Vector3f> normals = new ArrayList<>();
        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v,
                              int overlay, int light, float nx, float ny, float nz) {
            points.add(new Point(new Vector3f(x, y, z), u, v));
            normals.add(new Vector3f(nx, ny, nz));
        }
        public VertexConsumer addVertex(float x, float y, float z) { return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) { return this; }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
