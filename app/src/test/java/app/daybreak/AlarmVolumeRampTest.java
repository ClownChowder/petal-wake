package app.daybreak;

import org.junit.Test;
import static org.junit.Assert.*;

public class AlarmVolumeRampTest {
    @Test public void chosenStartAndDurationReachActualPhoneSteps(){
        assertEquals(2,AlarmVolumeRamp.step(7,1,7,30,60,0));
        assertEquals(5,AlarmVolumeRamp.step(7,1,7,30,60,30_000));
        assertEquals(7,AlarmVolumeRamp.step(7,1,7,30,60,60_000));
        assertEquals(7,AlarmVolumeRamp.step(7,1,7,30,60,90_000));
    }
    @Test public void selectedPercentAndDeviceMinimumAreHonored(){
        assertEquals(5,AlarmVolumeRamp.step(7,1,7,70,60,0));
        assertEquals(1,AlarmVolumeRamp.step(1,1,7,10,60,0));
        assertEquals(1,AlarmVolumeRamp.step(7,1,7,10,60,0));
        assertEquals(7,AlarmVolumeRamp.step(7,1,7,30,0,0));
        assertEquals(2,AlarmVolumeRamp.step(5,1,7,30,60,0));
        assertEquals(5,AlarmVolumeRamp.step(5,1,7,30,60,60_000));
        assertEquals(5,AlarmVolumeRamp.step(5,1,7,90,60,0));
    }
}
