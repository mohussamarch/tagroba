package app.masroufy.personal;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LocalFilesPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
