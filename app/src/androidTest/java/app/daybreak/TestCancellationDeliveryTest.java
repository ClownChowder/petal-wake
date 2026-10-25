package app.daybreak;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONObject;

/** Isolated-device check that cancelling a future test removes its OS delivery. */
public final class TestCancellationDeliveryTest extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    @Override public void onStart() {
        Context context = getTargetContext();
        Bundle result = new Bundle();
        JSONObject originalTests = Store.object(context, "testAlarms");
        String id = "test:qa-cancel-" + System.currentTimeMillis();
        try {
            long fireAt = System.currentTimeMillis() + 12_000;
            JSONObject tests = new JSONObject(originalTests.toString());
            tests.put(id, new JSONObject().put("id", id).put("kind", "test")
                    .put("title", "Disposable cancellation check").put("at", fireAt));
            Store.put(context, "testAlarms", tests);
            Sync.run(context, false);
            if (!Store.object(context, "scheduled").has(id))
                throw new Exception("Disposable test was not scheduled");

            Thread.sleep(2_000);
            Scheduler.cancel(context, id);
            tests = Store.object(context, "testAlarms");
            tests.remove(id);
            Store.put(context, "testAlarms", tests);
            Sync.run(context, false);
            if (Store.object(context, "scheduled").has(id))
                throw new Exception("Cancelled test remained in the schedule");

            Thread.sleep(Math.max(0, fireAt + 4_000 - System.currentTimeMillis()));
            if (Store.object(context, "current").has(id) || Store.object(context, "testAlarms").has(id))
                throw new Exception("Cancelled test was delivered after its trigger time");
            result.putString("summary", "Cancelled test remained silent four seconds past trigger time");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("error", failure.toString());
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            Scheduler.cancel(context, id);
            Store.put(context, "testAlarms", originalTests);
            try { Sync.run(context, false); } catch (Exception ignored) { }
        }
    }
}
