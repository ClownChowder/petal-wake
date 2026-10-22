package app.daybreak;

import org.junit.Test;
import static org.junit.Assert.*;

public class AlarmEnvelopeTest {
    @Test public void audibleStartAndSmoothRiseRespectSelectedCeiling(){
        for(float target:new float[]{.1f,.8f,1f}){
            float previous=AlarmEnvelope.volume(target,60,0);
            assertEquals(target*.16f,previous,.00001f);
            for(long elapsed=0;elapsed<=61000;elapsed+=100){
                float actual=AlarmEnvelope.volume(target,60,elapsed);
                assertTrue(actual>=previous);assertTrue(actual<=target+.00001f);previous=actual;
            }
            assertEquals(target,AlarmEnvelope.volume(target,60,60000),.00001f);
        }
    }
    @Test public void offIsImmediateAndLongFadesStillStartAudibly(){
        assertEquals(.8f,AlarmEnvelope.volume(.8f,0,0),.00001f);
        assertEquals(.128f,AlarmEnvelope.volume(.8f,120,0),.00001f);
        assertEquals(.8f,AlarmEnvelope.volume(.8f,120,120000),.00001f);
    }
    @Test public void invalidInputsCannotExceedValidVolumeRange(){
        assertEquals(0f,AlarmEnvelope.volume(-1f,5,0),0f);
        assertEquals(1f,AlarmEnvelope.volume(2f,0,0),0f);
        assertEquals(.128f,AlarmEnvelope.volume(.8f,5,-100),.00001f);
    }
    @Test public void perceptualCurveMakesBuildupMorePronounced(){
        assertEquals(.16f,AlarmEnvelope.volume(1f,60,0),.00001f);
        assertEquals(.49f,AlarmEnvelope.volume(1f,60,30000),.00001f);
        assertEquals(1f,AlarmEnvelope.volume(1f,60,60000),.00001f);
        assertTrue(20*Math.log10(1f/AlarmEnvelope.volume(1f,60,0))>15);
    }
}
