package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";
    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FrameLayout frameBanner = view.findViewById(R.id.frame_banner_content);
        frameBanner.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        frameBanner.setClipToOutline(true);

        FrameLayout frameContent = view.findViewById(R.id.frame_banner_content);
        frameContent.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        frameContent.setClipToOutline(true);

        Button mPlayButton = view.findViewById(R.id.play_button);
        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
