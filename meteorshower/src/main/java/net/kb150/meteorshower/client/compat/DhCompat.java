package net.kb150.meteorshower.client.compat;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeApplyShaderRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import net.kb150.meteorshower.client.MeteorRenderer;
import org.joml.Matrix4f;

// Diese Klasse wird nur geladen, wenn Distant Horizons tatsächlich installiert ist.
// Sie hakt sich in das Render-Event von DH ein, kurz bevor DH die Szene zusammenfügt.
public class DhCompat {

    public static void init() {
        DhApi.events.bind(DhApiBeforeApplyShaderRenderEvent.class, new DhApiBeforeApplyShaderRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                DhApiRenderParam p = event.value;
                
                // NUR im transparenten Pass rendern, um doppeltes Zeichnen zu verhindern!
                if (p.renderPass == com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass.TRANSPARENT || 
                    p.renderPass == com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass.OPAQUE_AND_TRANSPARENT) {
                    
                    // DH Matrizen sicher in JOML Matrix4f umwandeln (Spalten-Major Konstruktor)
                    Matrix4f projMat = new Matrix4f(
                        p.dhProjectionMatrix.m00, p.dhProjectionMatrix.m10, p.dhProjectionMatrix.m20, p.dhProjectionMatrix.m30,
                        p.dhProjectionMatrix.m01, p.dhProjectionMatrix.m11, p.dhProjectionMatrix.m21, p.dhProjectionMatrix.m31,
                        p.dhProjectionMatrix.m02, p.dhProjectionMatrix.m12, p.dhProjectionMatrix.m22, p.dhProjectionMatrix.m32,
                        p.dhProjectionMatrix.m03, p.dhProjectionMatrix.m13, p.dhProjectionMatrix.m23, p.dhProjectionMatrix.m33
                    );
                    
                    Matrix4f mvMat = new Matrix4f(
                            p.dhModelViewMatrix.m00, p.dhModelViewMatrix.m10, p.dhModelViewMatrix.m20, p.dhModelViewMatrix.m30,
                            p.dhModelViewMatrix.m01, p.dhModelViewMatrix.m11, p.dhModelViewMatrix.m21, p.dhModelViewMatrix.m31,
                            p.dhModelViewMatrix.m02, p.dhModelViewMatrix.m12, p.dhModelViewMatrix.m22, p.dhModelViewMatrix.m32,
                            p.dhModelViewMatrix.m03, p.dhModelViewMatrix.m13, p.dhModelViewMatrix.m23, p.dhModelViewMatrix.m33
                    );

                    // Meteore direkt in den DH Framebuffer mit DH Depth und Matrizen zeichnen
                    MeteorRenderer.renderMeteorsForDH(projMat, mvMat, p.partialTicks);
                } // <-- DIESE KLAMMER HAT GEFEHLT!
            }
        });
    }
}