package hh.screenseek.app;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

@TargetApi(Build.VERSION_CODES.N)
/**
 * Quick Settings entry point for starting a ScreenSeek capture.
 *
 * The tile does not perform capture itself; it launches the Activity that
 * can request MediaProjection consent and then collapses Quick Settings.
 */
public class ScreenSeekTileService extends TileService {

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, CapturePromptActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAndCollapse(intent);
    }
}