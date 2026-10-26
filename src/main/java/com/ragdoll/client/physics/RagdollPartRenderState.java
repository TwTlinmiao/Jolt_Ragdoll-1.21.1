package com.ragdoll.client.physics;

public final class RagdollPartRenderState {

    private final String name;

    private float collisionHalfX;

    private float collisionHalfY;

    private float collisionHalfZ;

    private float collisionOffsetX;

    private float collisionOffsetY;

    private float collisionOffsetZ;

    private double x;

    private double y;

    private double z;

    private float rotationX;

    private float rotationY;

    private float rotationZ;

    private float rotationW;

    public RagdollPartRenderState(String name, float collisionHalfX, float collisionHalfY, float collisionHalfZ, float collisionOffsetX, float collisionOffsetY, float collisionOffsetZ) {
        this.name = name;
        this.collisionHalfX = collisionHalfX;
        this.collisionHalfY = collisionHalfY;
        this.collisionHalfZ = collisionHalfZ;
        this.collisionOffsetX = collisionOffsetX;
        this.collisionOffsetY = collisionOffsetY;
        this.collisionOffsetZ = collisionOffsetZ;
    }

    public RagdollPartRenderState(String name, double x, double y, double z, float rotationX, float rotationY, float rotationZ, float rotationW) {
        this(name, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        update(x, y, z, rotationX, rotationY, rotationZ, rotationW);
    }

    public void update(double x, double y, double z, float rotationX, float rotationY, float rotationZ, float rotationW) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.rotationX = rotationX;
        this.rotationY = rotationY;
        this.rotationZ = rotationZ;
        this.rotationW = rotationW;
    }

    public void updateCollisionBounds(float halfX, float halfY, float halfZ, float offsetX, float offsetY, float offsetZ) {
        this.collisionHalfX = halfX;
        this.collisionHalfY = halfY;
        this.collisionHalfZ = halfZ;
        this.collisionOffsetX = offsetX;
        this.collisionOffsetY = offsetY;
        this.collisionOffsetZ = offsetZ;
    }

    public String name() {
        return name;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float rotationX() {
        return rotationX;
    }

    public float rotationY() {
        return rotationY;
    }

    public float rotationZ() {
        return rotationZ;
    }

    public float rotationW() {
        return rotationW;
    }

    public float collisionHalfX() {
        return collisionHalfX;
    }

    public float collisionHalfY() {
        return collisionHalfY;
    }

    public float collisionHalfZ() {
        return collisionHalfZ;
    }

    public float collisionOffsetX() {
        return collisionOffsetX;
    }

    public float collisionOffsetY() {
        return collisionOffsetY;
    }

    public float collisionOffsetZ() {
        return collisionOffsetZ;
    }
}

