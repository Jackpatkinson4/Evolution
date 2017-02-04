package lemon.engine.evolution;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import lemon.engine.animation.AutomaticInterpolator;
import lemon.engine.animation.Interpolator;
import lemon.engine.animation.LinearInterpolator;
import lemon.engine.control.RenderEvent;
import lemon.engine.control.UpdateEvent;
import lemon.engine.entity.HeightMap;
import lemon.engine.entity.LineGraph;
import lemon.engine.entity.Quad;
import lemon.engine.entity.Skybox;
import lemon.engine.entity.SphereModelBuilder;
import lemon.engine.entity.TriangularIndexedModel;
import lemon.engine.event.EventManager;
import lemon.engine.event.Listener;
import lemon.engine.event.Subscribe;
import lemon.engine.frameBuffer.FrameBuffer;
import lemon.engine.function.CubicBezierCurve;
import lemon.engine.function.MollerTrumbore;
import lemon.engine.function.RaySphereIntersection;
import lemon.engine.game.Platform;
import lemon.engine.game.Player;
import lemon.engine.game.PlayerControls;
import lemon.engine.game.StandardControls;
import lemon.engine.input.CursorPositionEvent;
import lemon.engine.input.MouseButtonEvent;
import lemon.engine.input.MouseScrollEvent;
import lemon.engine.loader.SkyboxLoader;
import lemon.engine.math.Line;
import lemon.engine.math.MathUtil;
import lemon.engine.math.Matrix;
import lemon.engine.math.Projection;
import lemon.engine.math.Sphere;
import lemon.engine.math.Triangle;
import lemon.engine.math.Vector;
import lemon.engine.math.Vector3D;
import lemon.engine.render.MatrixType;
import lemon.engine.render.ShaderProgram;
import lemon.engine.terrain.TerrainGenerator;
import lemon.engine.texture.Texture;
import lemon.engine.texture.TextureBank;
import lemon.engine.time.BenchmarkEvent;
import lemon.engine.time.Benchmarker;

public enum Game implements Listener {
	INSTANCE;
	private static final Logger logger = Logger.getLogger(Game.class.getName());
	
	
	private Player player;
	
	private PlayerControls<Integer, Integer> controls;
	
	private HeightMap terrain;
	
	private static final float TILE_SIZE = 0.2f; //0.2f 1f
	
	private FrameBuffer frameBuffer;
	private Texture colorTexture;
	private Texture depthTexture;
	
	private Texture skyboxTexture;
	
	private Benchmarker benchmarker;
	
	private TerrainLoader terrainLoader;
	
	private List<Platform> platforms;
	
	private TriangularIndexedModel sphere;
	private TriangularIndexedModel model;
	
	public TerrainLoader getTerrainLoader(){
		if(terrainLoader==null){
			terrainLoader = new TerrainLoader(new TerrainGenerator(0), Math.max((int) (100f/TILE_SIZE), 2), Math.max((int) (100f/TILE_SIZE), 2));
		}
		return terrainLoader;
	}
	
	public void init(long window){
		logger.log(Level.FINE, "Initializing");
		IntBuffer width = BufferUtils.createIntBuffer(1);
		IntBuffer height = BufferUtils.createIntBuffer(1);
		GLFW.glfwGetWindowSize(window, width, height);
		int window_width = width.get();
		int window_height = height.get();
		
		GL11.glViewport(0, 0, window_width, window_height);
		
		Skybox.INSTANCE.init();
		Quad.TEXTURED.init();
		Quad.COLORED.init();
		terrain = new HeightMap(terrainLoader.getTerrain(), TILE_SIZE);
		
		sphere = new SphereModelBuilder(0.1f, 3).buildAndInit();
		Vector3D[] vectors = new Vector3D[50];
		int[] indices = new int[(vectors.length-1)*3];
		for(int i=0;i<vectors.length;++i){
			vectors[i] = new Vector3D(curve.apply(((float)i)/((float)(vectors.length-1))*10-5));
		}
		for(int i=0;i<indices.length;i+=3){
			indices[i] = 0;
			indices[i+1] = (i/3)+1;
			indices[i+2] = (i/3)+2;
		}
		model = new TriangularIndexedModel.Builder().addVertices(Vector3D.ZERO)
				.addVertices(vectors).addIndices(indices).buildAndInit();
		
		benchmarker = new Benchmarker();
		benchmarker.put("updateData", new LineGraph(1000, 100000000));
		benchmarker.put("renderData", new LineGraph(1000, 100000000));
		benchmarker.put("fpsData", new LineGraph(1000, 100));
		
		player = new Player(new Projection(60f, ((float)window_width)/((float)window_height), 0.01f, 1000f));
		Matrix projectionMatrix = player.getCamera().getProjectionMatrix();
		
		CommonPrograms3D.initAll();
		
		GL20.glUseProgram(CommonPrograms3D.COLOR.getShaderProgram().getId());
		CommonPrograms3D.COLOR.getShaderProgram().loadMatrix(MatrixType.MODEL_MATRIX, Matrix.IDENTITY_4);
		CommonPrograms3D.COLOR.getShaderProgram().loadMatrix(MatrixType.PROJECTION_MATRIX, projectionMatrix);
		GL20.glUseProgram(0);
		updateViewMatrix(CommonPrograms3D.COLOR.getShaderProgram());
		
		GL20.glUseProgram(CommonPrograms3D.TEXTURE.getShaderProgram().getId());
		CommonPrograms3D.TEXTURE.getShaderProgram().loadMatrix(MatrixType.MODEL_MATRIX, Matrix.IDENTITY_4);
		CommonPrograms3D.TEXTURE.getShaderProgram().loadMatrix(MatrixType.PROJECTION_MATRIX, projectionMatrix);
		CommonPrograms3D.TEXTURE.getShaderProgram().loadInt("textureSampler", TextureBank.REUSE.getId());
		GL20.glUseProgram(0);
		updateViewMatrix(CommonPrograms3D.TEXTURE.getShaderProgram());
		
		GL20.glUseProgram(CommonPrograms3D.CUBEMAP.getShaderProgram().getId());
		CommonPrograms3D.CUBEMAP.getShaderProgram().loadMatrix(MatrixType.PROJECTION_MATRIX, projectionMatrix);
		CommonPrograms3D.CUBEMAP.getShaderProgram().loadInt("cubemapSampler", TextureBank.SKYBOX.getId());
		GL20.glUseProgram(0);
		updateViewMatrix(CommonPrograms3D.CUBEMAP.getShaderProgram());
		
		GL20.glUseProgram(CommonPrograms3D.POST_PROCESSING.getShaderProgram().getId());
		CommonPrograms3D.POST_PROCESSING.getShaderProgram().loadInt("colorSampler", TextureBank.COLOR.getId());
		CommonPrograms3D.POST_PROCESSING.getShaderProgram().loadInt("depthSampler", TextureBank.DEPTH.getId());
		GL20.glUseProgram(0);
		
		CommonPrograms2D.initAll();
		
		GL20.glUseProgram(CommonPrograms2D.COLOR.getShaderProgram().getId());
		CommonPrograms2D.COLOR.getShaderProgram().loadMatrix(MatrixType.TRANSFORMATION_MATRIX, Matrix.IDENTITY_4);
		CommonPrograms2D.COLOR.getShaderProgram().loadMatrix(MatrixType.PROJECTION_MATRIX, Matrix.IDENTITY_4);
		GL20.glUseProgram(0);
		
		GL20.glUseProgram(CommonPrograms2D.LINE.getShaderProgram().getId());
		CommonPrograms2D.LINE.getShaderProgram().loadInt("index", 0);
		CommonPrograms2D.LINE.getShaderProgram().loadInt("total", 0);
		CommonPrograms2D.LINE.getShaderProgram().loadFloat("alpha", 1f);
		GL20.glUseProgram(0);
		
		GL20.glUseProgram(CommonPrograms2D.TEXT.getShaderProgram().getId());
		CommonPrograms2D.TEXT.getShaderProgram().loadMatrix(MatrixType.MODEL_MATRIX, Matrix.IDENTITY_4);
		CommonPrograms2D.TEXT.getShaderProgram().loadMatrix(MatrixType.VIEW_MATRIX, Matrix.IDENTITY_4);
		CommonPrograms2D.TEXT.getShaderProgram().loadMatrix(MatrixType.PROJECTION_MATRIX, Matrix.IDENTITY_4);
		CommonPrograms2D.TEXT.getShaderProgram().loadVector("color", Vector3D.ZERO);
		CommonPrograms2D.TEXT.getShaderProgram().loadInt("textureSampler", TextureBank.REUSE.getId());
		GL20.glUseProgram(0);
		
		frameBuffer = new FrameBuffer();
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer.getId());
		GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
		colorTexture = new Texture();
		GL13.glActiveTexture(TextureBank.COLOR.getBind());
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture.getId());
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, window_width, window_height, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
		GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, colorTexture.getId(), 0);
		depthTexture = new Texture();
		GL13.glActiveTexture(TextureBank.DEPTH.getBind());
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture.getId());
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT32, window_width, window_height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer)null);
		GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL32.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, depthTexture.getId(), 0);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		skyboxTexture = new Texture();
		GL13.glActiveTexture(TextureBank.SKYBOX.getBind());
		skyboxTexture.load(new SkyboxLoader(new File("res/darkskies/"), new File("res/darkskies/darkskies.cfg")).load());
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, skyboxTexture.getId());
		GL13.glActiveTexture(TextureBank.REUSE.getBind());
		
		controls = new StandardControls();
		controls.bindKey(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOUSE_BUTTON_LEFT);
		controls.bindKey(GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_A);
		controls.bindKey(GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_D);
		controls.bindKey(GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_W);
		controls.bindKey(GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_S);
		controls.bindKey(GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_SPACE);
		controls.bindKey(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_LEFT_SHIFT);
		controls.bindKey(GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_T);
		
		platforms = new ArrayList<Platform>();
		platforms.add(new Platform(new Vector3D(10f, 0f, 10f)));
		platforms.add(new Platform(new Vector3D(0f, 0f, 0f)));
		
		rayTriangleIntersection = new MollerTrumbore(true);
		raySphereIntersection = new RaySphereIntersection();
		interp = new AutomaticInterpolator(x, new LinearInterpolator(new Vector(5f, 5f, 5f)), f->BezierCurves.EASE_OUT.apply(Interpolator.clamp(f/3000000000f)).get(1));
		EventManager.INSTANCE.registerListener(this);
	}
	CubicBezierCurve curve = new CubicBezierCurve(Vector3D.ZERO, 
			new Vector3D(0.17f, 0.67f, 0f), new Vector3D(0.83f, 0.67f, 0f), new Vector3D(0f, 0f, -1f));
	AutomaticInterpolator interp;
	private static float friction = 0.98f;
	private static float maxSpeed = 0.03f;
	private static float playerSpeed = maxSpeed-maxSpeed*friction;
	private float globalTime = -1000000000;
	@Subscribe
	public void update(UpdateEvent event){
		globalTime+=event.getDelta();
		interp.update(globalTime);
		if(controls.hasStates()){
			float angle = (player.getCamera().getRotation().getY()+90)*(((float)Math.PI)/180f);
			float sin = (float)Math.sin(angle);
			float cos = (float)Math.cos(angle);
			if(controls.getState(GLFW.GLFW_KEY_A)){
				player.getVelocity().setX(player.getVelocity().getX()-((float)(playerSpeed))*sin);
				player.getVelocity().setZ(player.getVelocity().getZ()-((float)(playerSpeed))*cos);
			}
			if(controls.getState(GLFW.GLFW_KEY_D)){
				player.getVelocity().setX(player.getVelocity().getX()+((float)(playerSpeed))*sin);
				player.getVelocity().setZ(player.getVelocity().getZ()+((float)(playerSpeed))*cos);
			}
			angle = player.getCamera().getRotation().getY()*(((float)Math.PI)/180f);
			sin = (float)Math.sin(angle);
			cos = (float)Math.cos(angle);
			if(controls.getState(GLFW.GLFW_KEY_W)){
				player.getVelocity().setX(player.getVelocity().getX()-((float)(playerSpeed))*sin);
				player.getVelocity().setZ(player.getVelocity().getZ()-((float)(playerSpeed))*cos);
			}
			if(controls.getState(GLFW.GLFW_KEY_S)){
				player.getVelocity().setX(player.getVelocity().getX()+((float)(playerSpeed))*sin);
				player.getVelocity().setZ(player.getVelocity().getZ()+((float)(playerSpeed))*cos);
			}
			if(controls.getState(GLFW.GLFW_KEY_SPACE)){
				player.getVelocity().setY(player.getVelocity().getY()+((float)(playerSpeed)));
			}
			if(controls.getState(GLFW.GLFW_KEY_LEFT_SHIFT)){
				player.getVelocity().setY(player.getVelocity().getY()-((float)(playerSpeed)));
			}
		}
		player.getVelocity().setX(player.getVelocity().getX()*friction);
		player.getVelocity().setY(player.getVelocity().getY()*friction);
		player.getVelocity().setZ(player.getVelocity().getZ()*friction);
		
		player.update(event);
		updateViewMatrix(CommonPrograms3D.COLOR.getShaderProgram());
		updateViewMatrix(CommonPrograms3D.TEXTURE.getShaderProgram());
		updateCubeMapMatrix(CommonPrograms3D.CUBEMAP.getShaderProgram());
	}
	@Subscribe
	public void onMouseScroll(MouseScrollEvent event){
		playerSpeed+=(float)(event.getYOffset()/100f);
		if(playerSpeed<0){
			playerSpeed = 0;
		}
		player.getCamera().getProjection().setFov(player.getCamera().getProjection().getFov()+((float)(event.getYOffset()/100f)));
		updateProjectionMatrix(CommonPrograms3D.COLOR.getShaderProgram());
		updateProjectionMatrix(CommonPrograms3D.TEXTURE.getShaderProgram());
		updateProjectionMatrix(CommonPrograms3D.CUBEMAP.getShaderProgram());
	}
	private double lastMouseX;
	private double lastMouseY;
	private double mouseX;
	private double mouseY;
	private static final float MOUSE_SENSITIVITY = 0.1f;
	@Subscribe
	public void onMousePosition(CursorPositionEvent event){
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		mouseX = event.getX();
		mouseY = event.getY();
		if(controls.getState(GLFW.GLFW_MOUSE_BUTTON_1)){
			player.getCamera().getRotation().setY((float) (((player.getCamera().getRotation().getY()-(mouseX-lastMouseX)*MOUSE_SENSITIVITY)%360)+360)%360);
			player.getCamera().getRotation().setX((float) (player.getCamera().getRotation().getX()-(mouseY-lastMouseY)*MOUSE_SENSITIVITY));
			if(player.getCamera().getRotation().getX()<-90){
				player.getCamera().getRotation().setX(-90);
			}
			if(player.getCamera().getRotation().getX()>90){
				player.getCamera().getRotation().setX(90);
			}
		}
		updateViewMatrix(CommonPrograms3D.COLOR.getShaderProgram());
		updateViewMatrix(CommonPrograms3D.TEXTURE.getShaderProgram());
		updateCubeMapMatrix(CommonPrograms3D.CUBEMAP.getShaderProgram());
	}
	public void updateViewMatrix(ShaderProgram program){
		GL20.glUseProgram(program.getId());
		program.loadMatrix(MatrixType.VIEW_MATRIX,
				player.getCamera().getInvertedRotationMatrix().multiply(player.getCamera().getInvertedTranslationMatrix()));
		GL20.glUseProgram(0);
	}
	public void updateCubeMapMatrix(ShaderProgram program){
		GL20.glUseProgram(program.getId());
		program.loadMatrix(MatrixType.VIEW_MATRIX, player.getCamera().getInvertedRotationMatrix());
		GL20.glUseProgram(0);
	}
	public void updateProjectionMatrix(ShaderProgram program){
		GL20.glUseProgram(program.getId());
		program.loadMatrix(MatrixType.PROJECTION_MATRIX, player.getCamera().getProjectionMatrix());
		GL20.glUseProgram(0);
	}
	@Subscribe
	public void render(RenderEvent event){
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, frameBuffer.getId());
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glDepthMask(false);
		renderSkybox();
		GL11.glDepthMask(true);
		renderHeightMap();
		renderPlatforms();
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		GL20.glUseProgram(CommonPrograms3D.POST_PROCESSING.getShaderProgram().getId());
		Quad.TEXTURED.render();
		GL20.glUseProgram(0);
		renderFPS();
	}
	Vector3D x = new Vector3D(Vector3D.ZERO);
	public void renderHeightMap(){
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL20.glUseProgram(CommonPrograms3D.COLOR.getShaderProgram().getId());
		CommonPrograms3D.COLOR.getShaderProgram().loadMatrix(MatrixType.MODEL_MATRIX, Matrix.IDENTITY_4);
		terrain.render();
		model.render();
		CommonPrograms3D.COLOR.getShaderProgram().loadMatrix(MatrixType.MODEL_MATRIX, MathUtil.getTranslation(x));
		sphere.render();
		CommonPrograms3D.COLOR.getShaderProgram().loadMatrix(MatrixType.MODEL_MATRIX, MathUtil.getTranslation(new Vector3D(curve.getA())));
		sphere.render();
		CommonPrograms3D.COLOR.getShaderProgram().loadMatrix(MatrixType.MODEL_MATRIX, MathUtil.getTranslation(new Vector3D(curve.getB())));
		sphere.render();
		CommonPrograms3D.COLOR.getShaderProgram().loadMatrix(MatrixType.MODEL_MATRIX, MathUtil.getTranslation(new Vector3D(curve.getC())));
		sphere.render();
		CommonPrograms3D.COLOR.getShaderProgram().loadMatrix(MatrixType.MODEL_MATRIX, MathUtil.getTranslation(new Vector3D(curve.getD())));
		sphere.render();
		GL20.glUseProgram(0);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
	}
	public void renderPlatforms(){
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		for(Platform platform: platforms){
			GL20.glUseProgram(CommonPrograms3D.COLOR.getShaderProgram().getId());
			CommonPrograms3D.COLOR.getShaderProgram().loadMatrix(MatrixType.MODEL_MATRIX.getUniformVariableName(),
					MathUtil.getTranslation(platform.getPosition()).multiply(MathUtil.getRotationX(90f)));
			//Quad.COLORED.render();
			GL20.glUseProgram(0);
		}
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
	}
	public void renderSkybox(){
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL20.glUseProgram(CommonPrograms3D.CUBEMAP.getShaderProgram().getId());
		Skybox.INSTANCE.render();
		GL20.glUseProgram(0);
		GL11.glDisable(GL11.GL_BLEND);
	}
	public void renderFPS(){
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL20.glUseProgram(CommonPrograms2D.LINE.getShaderProgram().getId());
		byte color = 1; //Not Black
		for(String benchmarker: this.benchmarker.getNames()){
			CommonPrograms2D.LINE.getShaderProgram().loadVector("color", new Vector3D((((color&0x01)!=0)?1f:0f), (((color&0x02)!=0)?1f:0f), (((color&0x04)!=0)?1f:0f)));
			CommonPrograms2D.LINE.getShaderProgram().loadFloat("spacing", 2f/(this.benchmarker.getLineGraph(benchmarker).getSize()-1));
			this.benchmarker.getLineGraph(benchmarker).render();
			color++;
		}
		GL20.glUseProgram(0);
		GL11.glDisable(GL11.GL_BLEND);
	}
	@Subscribe
	public void onBenchmark(BenchmarkEvent event){
		benchmarker.benchmark(event.getBenchmark());
	}
	private MollerTrumbore rayTriangleIntersection;
	private RaySphereIntersection raySphereIntersection;
	@Subscribe
	public void onClick(MouseButtonEvent event){
		if(event.getAction()==GLFW.GLFW_RELEASE){
			if(event.getButton()==GLFW.GLFW_MOUSE_BUTTON_1){
				Line line = new Line(player.getCamera().getPosition(), player.getVectorDirection());
				System.out.println(rayTriangleIntersection.apply(new Triangle(new Vector3D(-1f, 0f, -1f), new Vector3D(-1f, 0f, 1f), new Vector3D(1f, 0f, 0f)),
						line));
				System.out.println(raySphereIntersection.apply(line, new Sphere(x, 1f)));
			}
		}
	}
}
