package com.example.oxyarena.client.particle;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class NevoaBorderParticle extends TextureSheetParticle {
    private static final float FADE_IN_PORTION = 0.10F;
    private static final float FULL_SIZE_PORTION = 0.42F;
    private static final float FADE_OUT_START_PORTION = 0.78F;
    private static final float MAX_ALPHA = 0.93F;
    private static final float MIN_END_ALPHA = 0.24F;

    private final SpriteSet sprites;
    private final float baseYaw;
    private final float rotationSpeed;

    private NevoaBorderParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            SpriteSet sprites) {
        super(level, x, y, z, xSpeed * 0.18D, ySpeed * 0.05D + 0.01D, zSpeed * 0.18D);
        this.sprites = sprites;
        this.hasPhysics = false;
        this.friction = 0.92F;
        this.gravity = 0.0F;
        this.quadSize = 0.55F + this.random.nextFloat() * 0.35F;
        this.lifetime = 16 + this.random.nextInt(8);
        this.baseYaw = this.random.nextFloat() * ((float)Math.PI * 2.0F);
        this.rotationSpeed = (this.random.nextFloat() - 0.5F) * 0.07F;
        float gray = 0.58F + this.random.nextFloat() * 0.16F;
        this.setColor(gray, gray, gray + 0.03F);
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
            this.setAlpha(this.computeAlpha(0.0F));
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float alpha = this.computeAlpha(partialTicks);
        if (alpha <= 0.01F) {
            return;
        }

        this.alpha = alpha;
        float cubeRadius = this.getQuadSize(partialTicks) * 0.5F;
        Vec3 cameraPos = camera.getPosition();
        float renderX = (float)(Mth.lerp(partialTicks, this.xo, this.x) - cameraPos.x());
        float renderY = (float)(Mth.lerp(partialTicks, this.yo, this.y) - cameraPos.y());
        float renderZ = (float)(Mth.lerp(partialTicks, this.zo, this.z) - cameraPos.z());
        Quaternionf rotation = new Quaternionf().rotationY(this.baseYaw + ((float)this.age + partialTicks) * this.rotationSpeed);
        Vector3f[] corners = this.buildCubeVertices(cubeRadius, rotation, renderX, renderY, renderZ);
        int packedLight = this.getLightColor(partialTicks);
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();

        this.renderFace(buffer, corners[4], corners[5], corners[6], corners[7], u0, u1, v0, v1, packedLight);
        this.renderFace(buffer, corners[1], corners[0], corners[3], corners[2], u0, u1, v0, v1, packedLight);
        this.renderFace(buffer, corners[0], corners[4], corners[7], corners[3], u0, u1, v0, v1, packedLight);
        this.renderFace(buffer, corners[5], corners[1], corners[2], corners[6], u0, u1, v0, v1, packedLight);
        this.renderFace(buffer, corners[3], corners[7], corners[6], corners[2], u0, u1, v0, v1, packedLight);
        this.renderFace(buffer, corners[0], corners[1], corners[5], corners[4], u0, u1, v0, v1, packedLight);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 240;
    }

    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        double extent = Math.max(1.0D, this.getQuadSize(partialTicks));
        return new AABB(
                this.x - extent,
                this.y - extent,
                this.z - extent,
                this.x + extent,
                this.y + extent,
                this.z + extent);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float progress = (((float)this.age) + partialTicks) / (float)this.lifetime;
        if (progress <= FULL_SIZE_PORTION) {
            return this.quadSize * Mth.lerp(progress / FULL_SIZE_PORTION, 0.45F, 1.15F);
        }

        float shrinkProgress = (progress - FULL_SIZE_PORTION) / (1.0F - FULL_SIZE_PORTION);
        return this.quadSize * Mth.lerp(shrinkProgress, 1.15F, 0.30F);
    }

    private float computeAlpha(float partialTicks) {
        float progress = (((float)this.age) + partialTicks) / (float)this.lifetime;
        if (progress <= FADE_IN_PORTION) {
            return Mth.lerp(progress / FADE_IN_PORTION, 0.0F, MAX_ALPHA);
        }

        if (progress <= FADE_OUT_START_PORTION) {
            return MAX_ALPHA;
        }

        return Mth.lerp(
                (progress - FADE_OUT_START_PORTION) / (1.0F - FADE_OUT_START_PORTION),
                MAX_ALPHA,
                MIN_END_ALPHA);
    }

    private Vector3f[] buildCubeVertices(float radius, Quaternionf rotation, float x, float y, float z) {
        return new Vector3f[] {
                new Vector3f(-radius, -radius, -radius).rotate(rotation).add(x, y, z),
                new Vector3f(radius, -radius, -radius).rotate(rotation).add(x, y, z),
                new Vector3f(radius, radius, -radius).rotate(rotation).add(x, y, z),
                new Vector3f(-radius, radius, -radius).rotate(rotation).add(x, y, z),
                new Vector3f(-radius, -radius, radius).rotate(rotation).add(x, y, z),
                new Vector3f(radius, -radius, radius).rotate(rotation).add(x, y, z),
                new Vector3f(radius, radius, radius).rotate(rotation).add(x, y, z),
                new Vector3f(-radius, radius, radius).rotate(rotation).add(x, y, z)
        };
    }

    private void renderFace(
            VertexConsumer buffer,
            Vector3f vertex0,
            Vector3f vertex1,
            Vector3f vertex2,
            Vector3f vertex3,
            float u0,
            float u1,
            float v0,
            float v1,
            int packedLight) {
        this.addVertex(buffer, vertex0, u1, v1, packedLight);
        this.addVertex(buffer, vertex1, u0, v1, packedLight);
        this.addVertex(buffer, vertex2, u0, v0, packedLight);
        this.addVertex(buffer, vertex3, u1, v0, packedLight);
    }

    private void addVertex(VertexConsumer buffer, Vector3f vertex, float u, float v, int packedLight) {
        buffer.addVertex(vertex.x(), vertex.y(), vertex.z())
                .setUv(u, v)
                .setColor(this.rCol, this.gCol, this.bCol, this.alpha)
                .setLight(packedLight);
    }

    @OnlyIn(Dist.CLIENT)
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new NevoaBorderParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
