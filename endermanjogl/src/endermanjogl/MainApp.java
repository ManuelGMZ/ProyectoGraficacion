
package endermanjogl;

import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;
import javax.swing.*;
import java.awt.event.*;
import java.util.function.BiConsumer;

public class MainApp {
    // Valores base para ajustes rápidos vía teclado (escala, rotación, movimiento)
    private static final float SCALE = 0.10f;
    private static final float ROT = 15.0f;
    private static final float MOVE = 5.0f;

    public static void main(String[] args) {
        // Inicializa el perfil OpenGL (GL2) y el panel donde se dibuja
        GLProfile.initSingleton();
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities caps = new GLCapabilities(profile);
        caps.setDoubleBuffered(true);
        caps.setDepthBits(24);
        GLJPanel canvas = new GLJPanel(caps);

        // Nuestro renderer del Enderman (escena y controles)
        EndermanRenderer r = new EndermanRenderer();
        canvas.addGLEventListener(r);

        // Ventana básica con el canvas adentro
        JFrame frame = new JFrame("Enderman");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(canvas);
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Animador a 60 FPS para refrescar la escena
        FPSAnimator animator = new FPSAnimator(canvas, 60, true);
        animator.start();

        // Enfoca el canvas para recibir las teclas
        canvas.setFocusable(true);
        canvas.requestFocusInWindow();

        // Helper para registrar atajos de teclado y forzar repaint
        BiConsumer<String, Runnable> bind = (key, action) ->
            register(canvas, key, () -> { action.run(); canvas.display(); });

        // Controles de tamaño (escala por eje)
        bind.accept("Q", () -> r.scaleX += SCALE);
        bind.accept("A", () -> r.scaleX -= SCALE);
        bind.accept("W", () -> r.scaleY += SCALE);
        bind.accept("S", () -> r.scaleY -= SCALE);
        bind.accept("E", () -> r.scaleZ += SCALE);
        bind.accept("D", () -> r.scaleZ -= SCALE);

        // Controles de rotación (en grados por eje)
        bind.accept("R", () -> r.rotX += ROT);
        bind.accept("F", () -> r.rotX -= ROT);
        bind.accept("T", () -> r.rotY += ROT);
        bind.accept("G", () -> r.rotY -= ROT);
        bind.accept("Y", () -> r.rotZ += ROT);
        bind.accept("H", () -> r.rotZ -= ROT);

        // Traslaciones (con flechas y U/J/I/K/O/L)
        bind.accept("LEFT",  () -> { r.transX -= MOVE; r.markMoved(); });
        bind.accept("RIGHT", () -> { r.transX += MOVE; r.markMoved(); });
        bind.accept("UP",    () -> { r.transY += MOVE; r.markMoved(); });
        bind.accept("DOWN",  () -> { r.transY -= MOVE; r.markMoved(); });

        // Alternativas con letras 
        bind.accept("U", () -> { r.transX += MOVE; r.markMoved(); });
        bind.accept("J", () -> { r.transX -= MOVE; r.markMoved(); });
        bind.accept("I", () -> { r.transY += MOVE; r.markMoved(); });
        bind.accept("K", () -> { r.transY -= MOVE; r.markMoved(); });
        bind.accept("O", () -> { r.transZ += MOVE; r.markMoved(); });
        bind.accept("L", () -> { r.transZ -= MOVE; r.markMoved(); });

        // Zoom general (afecta X/Y/Z a la vez)
        bind.accept("PLUS",     () -> { r.scaleX += SCALE; r.scaleY += SCALE; r.scaleZ += SCALE; });
        bind.accept("MINUS",    () -> { r.scaleX -= SCALE; r.scaleY -= SCALE; r.scaleZ -= SCALE; });
        bind.accept("ADD",      () -> { r.scaleX += SCALE; r.scaleY += SCALE; r.scaleZ += SCALE; });
        bind.accept("SUBTRACT", () -> { r.scaleX -= SCALE; r.scaleY -= SCALE; r.scaleZ -= SCALE; });

        // Slideshow del suelo (ajusta periodo y fuerza siguiente textura)
        bind.accept("SHIFT pressed PLUS",  () -> r.slideshowPeriodSec = Math.max(1.0f, r.slideshowPeriodSec - 0.5f));
        bind.accept("SHIFT pressed MINUS", () -> r.slideshowPeriodSec = Math.min(30.0f, r.slideshowPeriodSec + 0.5f));
        bind.accept("SHIFT pressed S", r::forceNextGroundTexture);

        //Reset rápido de toda la transformación
        bind.accept("P", r::resetTransforms);
    }

    // Registra un atajo de teclado en el componente (WHEN_IN_FOCUSED_WINDOW)
    private static void register(JComponent comp, String key, Runnable action) {
        KeyStroke ks = KeyStroke.getKeyStroke(key);
        if (ks == null) return;
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = comp.getActionMap();
        im.put(ks, key);
        am.put(key, new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { action.run(); } });
    }
}
