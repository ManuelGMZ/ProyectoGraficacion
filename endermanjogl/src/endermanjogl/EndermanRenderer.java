
package endermanjogl;

import com.jogamp.opengl.*;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.*;

public class EndermanRenderer implements GLEventListener {
    // Transformaciones del modelo
    public float scaleX = 1.20f, scaleY = 1.20f, scaleZ = 1.20f;
    public float rotX = 0f, rotY = 0f, rotZ = 0f;
    public float transX = 0f, transY = 0f, transZ = -220f;

    // Marcha
    private float walkPhase = 0f;
    private float armSwing = 0f;
    private float legSwing = 0f;
    private long lastMoveNanos = 0L;

    // Texturas
    private Texture skyTex; // imagen fija del cielo
    private final List<Texture> groundList = new ArrayList<>(); //  imágenes del “gif” interno
    private int groundIdx = 0; // índice actual
    public float slideshowPeriodSec = 6.0f; // periodo del cambio
    private float slideshowTimer = 0f; // temporizador

    // Rutas
    private static final String PROP_DIR = "texturas.dir";
    private static final String ABS_DIR = "C:\\\\Users\\\\malej\\\\Documents\\\\NetBeansProjects\\\\endermanjogl\\\\src\\\\texturas\\\\";
    private static final String REL_DIR = "./texturas/";
    private static final String CP_DIR = "texturas/";

    // Cielo
    private static final String SKY_IMAGE_FILE = "cielo-con-nubes.png";
    // Suelo
    private static final String[] GROUND_FILES = { "arena.jpg", "Pasto.jpg", "Piedra.jpg" };

    // Constantes
    private static final float Y_GROUND = -90f; // altura del plano de suelo
    private final GLU glu = new GLU();
    private long lastTick = System.nanoTime();

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClearColor(1f, 1f, 1f, 1f);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LEQUAL);
        gl.glDisable(GL2.GL_CULL_FACE);

        // Iluminación básica
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        gl.glEnable(GL2.GL_NORMALIZE);
        float[] luz = { 0.3f, 1.0f, 0.2f, 0f }; // direccional
        float[] blanco = { 1f, 1f, 1f, 1f };
        float[] ambiente = { 0.30f, 0.30f, 0.30f, 1f };
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, luz, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, blanco, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_SPECULAR, blanco, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, ambiente, 0);

        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE);
        gl.glShadeModel(GL2.GL_SMOOTH);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);

        // Cargar cielo
        skyTex = loadTextureMulti(gl, SKY_IMAGE_FILE);

        // Cargar las imágenes del suelo
        for (String gf : GROUND_FILES) {
            Texture t = loadTextureMulti(gl, gf);
            if (t != null) groundList.add(t);
        }
        if (groundList.isEmpty()) {
            Texture fallback = loadTextureMulti(gl, "arena.jpg");
            if (fallback != null) groundList.add(fallback);
        }
    }

    @Override public void dispose(GLAutoDrawable drawable) { }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glViewport(0, 0, w, h);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        float aspect = (h == 0) ? 1f : (float) w / (float) h;
        glu.gluPerspective(60.0, aspect, 1.0, 1000.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        // Tiempo y animación
        long now = System.nanoTime();
        float dt = (now - lastTick) / 1_000_000_000f;
        lastTick = now;

        boolean moving = (now - lastMoveNanos) < 150_000_000L;
        if (moving) {
            walkPhase += 8f * dt;
            armSwing = (float) Math.sin(walkPhase) * 18f;
            legSwing = (float) Math.sin(walkPhase) * 18f;
        } else {
            armSwing *= 0.92f;
            legSwing *= 0.92f;
        }

        // Slideshow del suelo
        slideshowTimer += dt;
        if (slideshowTimer >= slideshowPeriodSec && groundList.size() > 1) {
            slideshowTimer = 0f;
            groundIdx = (groundIdx + 1) % groundList.size();
        }

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

        // Cielo en ORTO al fondo
        drawSky2D(gl);

        // Suelo 3D, centrado bajo el Enderman
        drawGround3D(gl);

        // Enderman
        applyDarkMaterial(gl);
        gl.glLoadIdentity();
        gl.glTranslatef(transX, transY, transZ);
        gl.glRotatef(rotX, 1f, 0f, 0f);
        gl.glRotatef(rotY, 0f, 1f, 0f);
        gl.glRotatef(rotZ, 0f, 0f, 1f);
        gl.glScalef(scaleX, scaleY, scaleZ);
        drawEnderman(gl);

        gl.glFlush();
    }

    // Cielo en 2D ORTO
    private void drawSky2D(GL2 gl) {
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDisable(GL2.GL_LIGHTING);
        int[] vp = new int[4]; gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        int w = Math.max(1, vp[2]), h = Math.max(1, vp[3]);
        gl.glMatrixMode(GL2.GL_PROJECTION); gl.glPushMatrix(); gl.glLoadIdentity();
        gl.glOrtho(0, w, 0, h, -1, 1);
        gl.glMatrixMode(GL2.GL_MODELVIEW); gl.glPushMatrix(); gl.glLoadIdentity();

        if (skyTex != null) {
            gl.glEnable(GL2.GL_TEXTURE_2D);
            skyTex.bind(gl);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glColor3f(1f, 1f, 1f);
            gl.glBegin(GL2.GL_QUADS);
            gl.glTexCoord2f(0f, 0f); gl.glVertex3f(0, 0, 0);
            gl.glTexCoord2f(0f, 1f); gl.glVertex3f(0, h, 0);
            gl.glTexCoord2f(1f, 1f); gl.glVertex3f(w, h, 0);
            gl.glTexCoord2f(1f, 0f); gl.glVertex3f(w, 0, 0);
            gl.glEnd();
            gl.glDisable(GL2.GL_TEXTURE_2D);
        }
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_PROJECTION); gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glDepthMask(true);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_LIGHTING);
    }

    // Suelo 3D
    private void drawGround3D(GL2 gl) {
        Texture groundTex = groundList.isEmpty() ? null : groundList.get(groundIdx);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glTranslatef(transX, 0f, transZ); // suelo bajo el Enderman
        float half = 280f;
        float uRep = 6.0f, vRep = 6.0f;

        if (groundTex != null) {
            gl.glEnable(GL2.GL_TEXTURE_2D);
            groundTex.bind(gl);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
            gl.glColor3f(1f, 1f, 1f);
        } else {
            gl.glDisable(GL2.GL_TEXTURE_2D);
            gl.glColor3f(0.85f, 0.80f, 0.65f);
        }

        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0f, 1f, 0f);
        if (groundTex != null) gl.glTexCoord2f(0f, 0f);
        gl.glVertex3f(-half, Y_GROUND, -half);
        if (groundTex != null) gl.glTexCoord2f(0f, vRep);
        gl.glVertex3f(-half, Y_GROUND, half);
        if (groundTex != null) gl.glTexCoord2f(uRep, vRep);
        gl.glVertex3f(half, Y_GROUND, half);
        if (groundTex != null) gl.glTexCoord2f(uRep, 0f);
        gl.glVertex3f(half, Y_GROUND, -half);
        gl.glEnd();

        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glPopMatrix();
    }

    // Enderman
    private void drawEnderman(GL2 gl) {
        drawHead(gl);
        drawEyes(gl);
        drawBody(gl);
        gl.glPushMatrix(); pivotRotate(gl, -10f, -5f, -2.5f, +legSwing); drawLeftLeg(gl); gl.glPopMatrix();
        gl.glPushMatrix(); pivotRotate(gl, +10f, -5f, -2.5f, -legSwing); drawRightLeg(gl); gl.glPopMatrix();
        gl.glPushMatrix(); pivotRotate(gl, -29f, 50f, -2.5f, -armSwing); drawLeftArm(gl); gl.glPopMatrix();
        gl.glPushMatrix(); pivotRotate(gl, +29f, 50f, -2.5f, +armSwing); drawRightArm(gl); gl.glPopMatrix();
    }

    private void pivotRotate(GL2 gl, float px, float py, float pz, float angleDeg) {
        gl.glTranslatef(px, py, pz);
        gl.glRotatef(angleDeg, 1f, 0f, 0f);
        gl.glTranslatef(-px, -py, -pz);
    }

    // Cabeza / Ojos / Cuerpo
    private void drawHead(GL2 gl) {
        gl.glColor3f(0f, 0f, 0f);
        gl.glNormal3f(0f, 0f, 1f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-23, 50, 0); gl.glVertex3f(-23, 95, 0);
        gl.glVertex3f( 23, 95, 0); gl.glVertex3f( 23, 50, 0); gl.glEnd();

        gl.glNormal3f(-1f,0f,0f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-23, 50, 0); gl.glVertex3f(-23, 95, 0);
        gl.glVertex3f(-23, 95, -23); gl.glVertex3f(-23, 50, -23); gl.glEnd();

        gl.glNormal3f( 1f,0f,0f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 23, 50, 0); gl.glVertex3f( 23, 95, 0);
        gl.glVertex3f( 23, 95, -23); gl.glVertex3f( 23, 50, -23); gl.glEnd();

        gl.glNormal3f(0f,0f,-1f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-23, 50, -23); gl.glVertex3f(-23, 95, -23);
        gl.glVertex3f( 23, 95, -23); gl.glVertex3f( 23, 50, -23); gl.glEnd();

        gl.glNormal3f(0f,1f,0f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-23, 95, 0); gl.glVertex3f(-23, 95, -23);
        gl.glVertex3f( 23, 95, -23); gl.glVertex3f( 23, 95, 0); gl.glEnd();

        gl.glNormal3f(0f,-1f,0f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-23, 50, 0); gl.glVertex3f(-23, 50, -23);
        gl.glVertex3f( 23, 50, -23); gl.glVertex3f( 23, 50, 0); gl.glEnd();
    }

    private void drawEyes(GL2 gl) {
        gl.glDisable(GL2.GL_COLOR_MATERIAL);
        float[] emitPink = {0.9f, 0.2f, 0.9f, 1f};
        float[] emitMagenta = {0.96f, 0f, 0.50f, 1f};
        float[] none = {0f, 0f, 0f, 0f};
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_EMISSION, emitPink, 0);
        gl.glNormal3f(0f, 0f, 1f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-22, 68, 0.20f); gl.glVertex3f(-22, 75, 0.20f);
        gl.glVertex3f( -3, 75, 0.20f); gl.glVertex3f( -3, 68, 0.20f); gl.glEnd();
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(22, 68, 0.20f); gl.glVertex3f(22, 75, 0.20f);
        gl.glVertex3f( 3, 75, 0.20f); gl.glVertex3f( 3, 68, 0.20f); gl.glEnd();

        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_EMISSION, emitMagenta, 0);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-17, 68, 0.21f); gl.glVertex3f(-17, 75, 0.21f);
        gl.glVertex3f( -9, 75, 0.21f); gl.glVertex3f( -9, 68, 0.21f); gl.glEnd();
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(17, 68, 0.21f); gl.glVertex3f(17, 75, 0.21f);
        gl.glVertex3f( 9, 75, 0.21f); gl.glVertex3f( 9, 68, 0.21f); gl.glEnd();

        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_EMISSION, none, 0);
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
    }

    private void drawBody(GL2 gl) {
        gl.glColor3f(0f, 0f, 0f);
        // frontal
        gl.glNormal3f(0f, 0f, 1f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-25, -5, -5); gl.glVertex3f(-25, 50, -5);
        gl.glVertex3f( 25, 50, -5); gl.glVertex3f( 25, -5, -5);
        gl.glEnd();

        // trasera
        gl.glNormal3f(0f, 0f,-1f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-25, -5, -18); gl.glVertex3f(-25, 50, -18);
        gl.glVertex3f( 25, 50, -18); gl.glVertex3f( 25, -5, -18);
        gl.glEnd();

        // laterales
        gl.glNormal3f(-1f,0f,0f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-25, -5, 0); gl.glVertex3f(-25, 50, 0);
        gl.glVertex3f(-25, 50, -18); gl.glVertex3f(-25, -5, -18);
        gl.glEnd();

        gl.glNormal3f( 1f,0f,0f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 25, -5, 0); gl.glVertex3f( 25, 50, 0);
        gl.glVertex3f( 25, 50, -18); gl.glVertex3f( 25, -5, -18);
        gl.glEnd();

        // tapas
        gl.glNormal3f(0f,-1f,0f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-25, -5, 0); gl.glVertex3f(-25, -5, -18);
        gl.glVertex3f( 25, -5, -18); gl.glVertex3f( 25, -5, -5);
        gl.glEnd();

        gl.glNormal3f(0f, 1f, 0f); gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-25, 50, 0); gl.glVertex3f(-25, 50, -18);
        gl.glVertex3f( 25, 50, -18); gl.glVertex3f( 25, 50, -5);
        gl.glEnd();
    }

    // Piernas
    private void drawLeftLeg(GL2 gl) {
        gl.glColor3f(0f, 0f, 0f);
        // Frontal
        gl.glNormal3f(0f, 0f, 1f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-15, -90, -5); gl.glVertex3f(-15, -5, -5);
        gl.glVertex3f( -5, -5, -5); gl.glVertex3f( -5, -90, -5);
        gl.glEnd();

        // Lado dcha
        gl.glNormal3f( 1f, 0f, 0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( -5, -90, -5); gl.glVertex3f( -5, -5, -5);
        gl.glVertex3f( -5, -5, 0); gl.glVertex3f( -5, -90, 0);
        gl.glEnd();

        // Lado izq
        gl.glNormal3f(-1f, 0f, 0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-15, -90, -5); gl.glVertex3f(-15, -5, -5);
        gl.glVertex3f(-15, -5, 0); gl.glVertex3f(-15, -90, 0);
        gl.glEnd();

        // Trasera
        gl.glNormal3f(0f, 0f, -1f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-15, -90, 0); gl.glVertex3f(-15, -5, 0);
        gl.glVertex3f( -5, -5, 0); gl.glVertex3f( -5, -90, 0);
        gl.glEnd();

        // Tapas
        gl.glNormal3f(0f, 1f, 0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-15, -5, -5); gl.glVertex3f(-15, -5, 0);
        gl.glVertex3f( -5, -5, 0); gl.glVertex3f( -5, -5, -5);
        gl.glEnd();

        gl.glNormal3f(0f, -1f, 0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-15, -90, -5); gl.glVertex3f(-15, -90, 0);
        gl.glVertex3f( -5, -90, 0); gl.glVertex3f( -5, -90, -5);
        gl.glEnd();
    }

    private void drawRightLeg(GL2 gl) {
        gl.glColor3f(0f, 0f, 0f);
        // Frontal
        gl.glNormal3f(0f, 0f, 1f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 15, -90, -5); gl.glVertex3f( 15, -5, -5);
        gl.glVertex3f( 5, -5, -5); gl.glVertex3f( 5, -90, -5);
        gl.glEnd();

        // Lado izq
        gl.glNormal3f(-1f, 0f, 0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 5, -90, 0); gl.glVertex3f( 5, -5, 0);
        gl.glVertex3f( 5, -5, -5); gl.glVertex3f( 5, -90, -5);
        gl.glEnd();

        // Lado dcha
        gl.glNormal3f( 1f, 0f, 0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 15, -90, 0); gl.glVertex3f( 15, -5, 0);
        gl.glVertex3f( 15, -5, -5); gl.glVertex3f( 15, -90, -5);
        gl.glEnd();

        // Trasera
        gl.glNormal3f(0f, 0f, -1f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 15, -90, 0); gl.glVertex3f( 15, -5, 0);
        gl.glVertex3f( 5, -5, 0); gl.glVertex3f( 5, -90, 0);
        gl.glEnd();

        // Tapas
        gl.glNormal3f(0f, 1f, 0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 15, -5, 0); gl.glVertex3f( 15, -5, -5);
        gl.glVertex3f( 5, -5, -5); gl.glVertex3f( 5, -5, 0);
        gl.glEnd();

        gl.glNormal3f(0f, -1f, 0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 15, -90, 0); gl.glVertex3f( 15, -90, -5);
        gl.glVertex3f( 5, -90, -5); gl.glVertex3f( 5, -90, 0);
        gl.glEnd();
    }

    // Brazos
    private void drawLeftArm(GL2 gl) {
        gl.glColor3f(0f, 0f, 0f);
        // Frontal
        gl.glNormal3f(0f,0f,1f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-24, 50, -5); gl.glVertex3f(-34, 51, -5);
        gl.glVertex3f(-47, -71, -5); gl.glVertex3f(-37, -72, -5);
        gl.glEnd();

        // Lateral interno
        gl.glNormal3f(-1f,0f,0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-34, 51, 0); gl.glVertex3f(-34, 51, -5);
        gl.glVertex3f(-37, -72, -5); gl.glVertex3f(-37, -72, 0);
        gl.glEnd();

        // Lateral externo
        gl.glNormal3f( 1f,0f,0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-24, 50, 0); gl.glVertex3f(-24, 50, -5);
        gl.glVertex3f(-47, -71, -5); gl.glVertex3f(-47, -71, 0);
        gl.glEnd();

        // Trasera
        gl.glNormal3f(0f,0f,-1f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-24, 50, 0); gl.glVertex3f(-34, 51, 0);
        gl.glVertex3f(-47, -71, 0); gl.glVertex3f(-37, -72, 0);
        gl.glEnd();

        // Tapa superior
        gl.glNormal3f(0f,1f,0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-24, 50, 0); gl.glVertex3f(-24, 50, -5);
        gl.glVertex3f(-34, 51, -5); gl.glVertex3f(-34, 51, 0);
        gl.glEnd();

        // Tapa inferior
        gl.glNormal3f(0f,-1f,0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(-47, -71, 0); gl.glVertex3f(-47, -71, -5);
        gl.glVertex3f(-37, -72, -5); gl.glVertex3f(-37, -72, 0);
        gl.glEnd();
    }

    private void drawRightArm(GL2 gl) {
        gl.glColor3f(0f,0f,0f);
        // Frontal
        gl.glNormal3f(0f,0f,1f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 24, 50, -5); gl.glVertex3f( 34, 51, -5);
        gl.glVertex3f( 47, -71, -5); gl.glVertex3f( 37, -72, -5);
        gl.glEnd();

        // Lateral interno
        gl.glNormal3f(-1f,0f,0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 24, 50, 0); gl.glVertex3f( 24, 50, -5);
        gl.glVertex3f( 37, -72, -5); gl.glVertex3f( 37, -72, 0);
        gl.glEnd();

        // Lateral externo
        gl.glNormal3f( 1f,0f,0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 34, 51, 0); gl.glVertex3f( 34, 51, -5);
        gl.glVertex3f( 47, -72, -5); gl.glVertex3f( 47, -72, 0);
        gl.glEnd();

        // Trasera
        gl.glNormal3f(0f,0f,-1f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 24, 50, 0); gl.glVertex3f( 34, 51, 0);
        gl.glVertex3f( 47, -71, 0); gl.glVertex3f( 37, -72, 0);
        gl.glEnd();

        // Tapa superior
        gl.glNormal3f(0f,1f,0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 24, 50, 0); gl.glVertex3f( 24, 50, -5);
        gl.glVertex3f( 34, 51, -5); gl.glVertex3f( 34, 51, 0);
        gl.glEnd();

        // Tapa inferior
        gl.glNormal3f(0f,-1f,0f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f( 47, -71, 0); gl.glVertex3f( 47, -71, -5);
        gl.glVertex3f( 37, -72, -5); gl.glVertex3f( 37, -72, 0);
        gl.glEnd();
    }

    // Cuerpo
    private void applyDarkMaterial(GL2 gl) {
        gl.glDisable(GL2.GL_COLOR_MATERIAL);
        float[] dark = {0.15f, 0.15f, 0.15f, 1f};
        float[] spec = {0.15f, 0.15f, 0.15f, 1f};
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE, dark, 0);
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, spec, 0);
        gl.glMaterialf (GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, 12f);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
    }

    // Carga multifuente
    private Texture loadTextureMulti(GL2 gl, String fileName) {
        String propDir = System.getProperty(PROP_DIR);
        if (propDir != null && !propDir.trim().isEmpty()) {
            Texture t = tryFile(gl, join(propDir, fileName)); if (t != null) return t;
        }
        Texture abs = tryFile(gl, join(ABS_DIR, fileName)); if (abs != null) return abs;
        Texture rel = tryFile(gl, join(REL_DIR, fileName)); if (rel != null) return rel;
        Texture cp  = tryClasspath(gl, CP_DIR + fileName);  if (cp  != null) return cp;
        return null;
    }

    private Texture tryClasspath(GL2 gl, String cpPath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(cpPath)) {
            if (is != null) return TextureIO.newTexture(is, false, ext(cpPath));
        } catch (IOException ignored) {}
        return null;
    }

    private Texture tryFile(GL2 gl, String path) {
        try {
            File f = new File(path);
            if (f.exists()) return TextureIO.newTexture(f, true);
        } catch (IOException ignored) {}
        return null;
    }

    private String join(String dir, String file) {
        if (dir.endsWith("/") || dir.endsWith("\\"))
            return dir + file;
        return dir + File.separator + file;
    }

    private String ext(String path) {
        int dot = path.lastIndexOf('.');
        return (dot > 0 && dot < path.length() - 1)
            ? path.substring(dot + 1).toUpperCase()
            : TextureIO.JPG;
    }

    // API teclado
    public void markMoved() { lastMoveNanos = System.nanoTime(); }

    public void forceNextGroundTexture() {
        if (groundList.size() > 1) {
            groundIdx = (groundIdx + 1) % groundList.size();
            slideshowTimer = 0f;
        }
    }

    public void resetTransforms() {
        scaleX = scaleY = scaleZ = 1.20f;
        rotX = rotY = rotZ = 0f;
        transX = 0f; transY = 0f; transZ = -220f;
        walkPhase = 0f; armSwing = 0f; legSwing = 0f;
        lastMoveNanos = 0L;
        groundIdx = 0; slideshowTimer = 0f;
    }
}
