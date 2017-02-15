package lemon.engine.physics;

public interface Collidable {
	public float getIntersection(CollisionEffect effect);
	public void applyResponse();
}