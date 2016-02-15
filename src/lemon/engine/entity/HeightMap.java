package lemon.engine.entity;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import lemon.engine.render.Renderable;
import lemon.engine.render.VertexArray;

public class HeightMap implements Renderable {
	private VertexArray vertexArray;
	private float[][] map;
	private float tileSize;
	public HeightMap(float[][] map, float tileSize){
		this.map = new float[map.length][map[0].length];
		this.tileSize = tileSize;
		for(int i=0;i<map.length;++i){
			for(int j=0;j<map[0].length;++j){
				this.map[i][j] = map[i][j];
			}
		}
	}
	@Override
	public void init() {
		vertexArray = new VertexArray();
		GL30.glBindVertexArray(vertexArray.getId());
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vertexArray.generateVbo().getId());
		IntBuffer indicesBuffer = BufferUtils.createIntBuffer(6*(map.length-1)*(map[0].length-1));
		for(int i=0;i<map.length-1;++i){
			for(int j=0;j<map[0].length-1;++j){
				if((i+j)%2==0){
					indicesBuffer.put(i*map[0].length+j+1);
					indicesBuffer.put(i*map[0].length+j);
					indicesBuffer.put((i+1)*map[0].length+j);
					indicesBuffer.put((i+1)*map[0].length+j+1);
					indicesBuffer.put(i*map[0].length+j+1);
					indicesBuffer.put((i+1)*map[0].length+j);
				}else{
					indicesBuffer.put((i+1)*map[0].length+j);
					indicesBuffer.put((i+1)*map[0].length+j+1);
					indicesBuffer.put(i*map[0].length+j);
					indicesBuffer.put((i+1)*map[0].length+j+1);
					indicesBuffer.put(i*map[0].length+j+1);
					indicesBuffer.put(i*map[0].length+j);
				}
			}
		}
		indicesBuffer.flip();
		GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL15.GL_STATIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexArray.generateVbo().getId());
		FloatBuffer dataBuffer = BufferUtils.createFloatBuffer(map.length*map[0].length*7);
		for(int i=0;i<map.length;++i){
			for(int j=0;j<map[0].length;++j){
				dataBuffer.put(i*tileSize-(map.length*tileSize/2));
				dataBuffer.put(map[i][j]);
				dataBuffer.put(j*tileSize-(map[0].length*tileSize/2));
				dataBuffer.put(map[i][j]+0.3f);
				dataBuffer.put(map[i][j]-0.4f);
				dataBuffer.put(0f);
				dataBuffer.put(1f);
			}
		}
		dataBuffer.flip();
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, dataBuffer, GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 7*4, 0);
		GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 7*4, 3*4);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL20.glEnableVertexAttribArray(0);
		GL20.glEnableVertexAttribArray(1);
		GL30.glBindVertexArray(0);
	}
	@Override
	public VertexArray getVertexArray() {
		return vertexArray;
	}
	@Override
	public int getIndices(){
		return 6*(map.length-1)*(map[0].length-1);
	}
}
