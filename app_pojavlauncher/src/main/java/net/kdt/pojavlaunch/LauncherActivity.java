package net.kdt.pojavlaunch;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.security.ProviderInstaller;
import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcAccountSpinner;

import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.fragments.SelectAuthFragment;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.ForgeDownloadTask;
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy;
import net.kdt.pojavlaunch.modloaders.modpacks.ModloaderInstallTracker;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.utils.NotificationUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.lang.ref.WeakReference;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";
    public static boolean MODPACK_DOWNLOAD_FINISH = false;

    private static final ModItem DEFAULT_MODPACK = new ModItem(
            Constants.SOURCE_TECHNIC,
            true,
            "netpixelmon",
            "",
            "",
            ""
    );

    public final ActivityResultLauncher<Object> modInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(this, data);
            });

    private mcAccountSpinner mAccountSpinner;
    private FragmentContainerView mFragmentView;
    private ImageButton mHomeButton;
    private ImageButton mTiktokButton;
    private ImageButton mDiscordButton;
    private ImageButton mSettingsButton;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ModloaderInstallTracker mInstallTracker;
    private NotificationManager mNotificationManager;
    private String mSelectedProfile;
    private CommonApi api;

    /* Allows to switch from one button "type" to another */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            mHomeButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), f instanceof MainMenuFragment
                    ? R.drawable.ic_menu_home_active : R.drawable.ic_menu_home));
            mSettingsButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), f instanceof LauncherPreferenceFragment
                    ? R.drawable.ic_menu_settings_active : R.drawable.ic_menu_settings));
        }
    };

    /* Listener for the back button in settings */
    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if(value.equals("true")) onBackPressed();
        return false;
    };

    /* Listener for the auth method selection screen */
    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        // Allow starting the add account only from the main menu, should it be moved to fragment itself ?
        if(!(fragment instanceof MainMenuFragment)) return false;

        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        if(mProgressLayout.hasProcesses()){
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }

        mAccountSpinner = findViewById(R.id.account_spinner);
        if(mAccountSpinner.getSelectedAccount() == null){
            Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return false;
        }

        LauncherProfiles.load();

        for (String profileName : LauncherProfiles.mainProfileJson.profiles.keySet()) {
            MinecraftProfile prof = LauncherProfiles.mainProfileJson.profiles.get(profileName);
            if (prof != null && prof.name.toLowerCase().contains("forge")) {
                mSelectedProfile = profileName;
                break;
            }
        }

        boolean hasDefaultProfile = LauncherProfiles.mainProfileJson != null &&
                LauncherProfiles.mainProfileJson.profiles != null &&
                LauncherProfiles.mainProfileJson.profiles.containsKey(mSelectedProfile);

        if(!hasDefaultProfile) {
            Toast.makeText(this, "Instalando modpack padrão...", Toast.LENGTH_SHORT).show();

            installDefaultModpack(success -> {
                if (success) {
                    launchGameAfterModpackInstall(mSelectedProfile);
                } else {
                    Toast.makeText(LauncherActivity.this, "Falha na instalação do modpack padrão.", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            checkModpackVersion((updated, newVersion) -> {
                if (updated) {
                    launchGameAfterModpackInstall(mSelectedProfile);
                } else {
                    Toast.makeText(LauncherActivity.this, "Atualizando modpack.", Toast.LENGTH_SHORT).show();
                    installDefaultModpack(success -> {
                        if (success) {
                            LauncherPreferences.DEFAULT_PREF.edit()
                                    .putString(
                                            LauncherPreferences.PREF_MODPACK_VERSION,
                                            newVersion
                                    )
                                    .apply();

                            launchGameAfterModpackInstall(mSelectedProfile);
                        } else {
                            Toast.makeText(LauncherActivity.this, "Falha na instalação do modpack padrão.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }

        return false;
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        // Prevents the user from trying to launch the game with tasks ongoing.
        if(taskCount > 0) {
            Tools.runOnUiThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
    };

    private ActivityResultLauncher<String> mRequestNotificationPermissionLauncher;
    private WeakReference<Runnable> mRequestNotificationPermissionRunnable;

    @Override
    protected boolean shouldIgnoreNotch() {
        return getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean setFullscreen() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ProviderInstaller.installIfNeededAsync(this, new ProviderInstaller.ProviderInstallListener() {
            @Override
            public void onProviderInstalled() {
            }

            @Override
            public void onProviderInstallFailed(int i, Intent intent) {
            }
        });

        setContentView(R.layout.activity_pojav_launcher);
        FragmentManager fragmentManager = getSupportFragmentManager();
        // If we don't have a back stack root yet...
        if(fragmentManager.getBackStackEntryCount() < 1) {
            // Manually add the first fragment to the backstack to get easily back to it
            // There must be a better way to handle the root though...
            // (artDev: No, there is not. I've spent days researching this for another unrelated project.)
            fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack("ROOT")
                    .add(R.id.container_fragment, MainMenuFragment.class, null, "ROOT").commit();
        }


        IconCacheJanitor.runJanitor();
        mRequestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if(!isAllowed) handleNoNotificationPermission();
                    else {
                        Runnable runnable = Tools.getWeakReference(mRequestNotificationPermissionRunnable);
                        if(runnable != null) runnable.run();
                    }
                }
        );
        getWindow().setBackgroundDrawable(null);
        bindViews();
        checkNotificationPermission();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        mHomeButton.setOnClickListener(v -> Tools.swapFragment(this, MainMenuFragment.class, MainMenuFragment.TAG, null));
        mTiktokButton.setOnClickListener(v -> Tools.openURL(this, getString(R.string.tiktok_invite)));
        mDiscordButton.setOnClickListener(v -> Tools.openURL(this, getString(R.string.discord_invite)));
        mSettingsButton.setOnClickListener(v -> Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null));
        ProgressKeeper.addTaskCountListener(mProgressLayout);
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);

        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions), false);

        mInstallTracker = new ModloaderInstallTracker(this);
        api = new CommonApi(getString(R.string.curseforge_api_key));

        mProgressLayout.observe(ProgressLayout.DOWNLOAD_MINECRAFT);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE_MICROSOFT);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        mInstallTracker.attach();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
        mInstallTracker.detach();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    /** Custom implementation to feel more natural when a backstack isn't present */
    @Override
    public void onBackPressed() {
        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if(fragment != null){
            if(fragment.canGoBack()){
                fragment.goBack();
                return;
            }
        }

        // Check if we are at the root then
        if(getVisibleFragment("ROOT") != null){
            finish();
        }

        super.onBackPressed();
    }

    @Override
    public void onAttachedToWindow() {
        LauncherPreferences.computeNotchSize(this);
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag){
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Fragment getVisibleFragment(int id){
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    private void checkNotificationPermission() {
        if(LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
            checkForNotificationPermission()) {
            return;
        }

        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS)) {
            showNotificationPermissionReasoning();
            return;
        }
        askForNotificationPermission(null);
    }

    private void showNotificationPermissionReasoning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) -> askForNotificationPermission(null))
                .setNegativeButton(android.R.string.cancel, (d, w)-> handleNoNotificationPermission())
                .show();
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
        Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show();
    }

    public boolean checkForNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_DENIED;
    }

    public void askForNotificationPermission(Runnable onSuccessRunnable) {
        if(Build.VERSION.SDK_INT < 33) return;
        if(onSuccessRunnable != null) {
            mRequestNotificationPermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** Stuff all the view boilerplate here */
    private void bindViews(){
        mFragmentView = findViewById(R.id.container_fragment);
        mHomeButton = findViewById(R.id.home_button);
        mTiktokButton = findViewById(R.id.tiktok_button);
        mDiscordButton = findViewById(R.id.discord_button);
        mSettingsButton = findViewById(R.id.setting_button);
        mAccountSpinner = findViewById(R.id.account_spinner);
        mProgressLayout = findViewById(R.id.progress_layout);
    }

    public interface ModpackInstallListener {
        void onInstallComplete(boolean success);
    }

    private void installDefaultModpack(ModpackInstallListener listener) {
        LauncherProfiles.load();

        new Thread(() -> {
            try {
                ModloaderInstaller loaderInstaller = new ModloaderInstaller(this, ModloaderInstaller.LoaderType.FORGE);
                loaderInstaller.installLoader("1.16.5", "36.2.42");

                boolean loaderInstalled = false;
                while (!loaderInstalled) {
                    try {
                        LauncherProfiles.load();
                        for (String profileName : LauncherProfiles.mainProfileJson.profiles.keySet()) {
                            MinecraftProfile prof = LauncherProfiles.mainProfileJson.profiles.get(profileName);
                            if (prof != null && prof.name.toLowerCase().contains("forge")) {
                                mSelectedProfile = profileName;
                                loaderInstalled = true;
                            }
                        }

                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                    }
                }

                ModDetail detail = api.getModDetails(DEFAULT_MODPACK);
                api.handleInstallation(this, detail, 0);

                while (!MODPACK_DOWNLOAD_FINISH) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                }

                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(
                                LauncherPreferences.PREF_KEY_CURRENT_PROFILE,
                                mSelectedProfile
                        )
                        .apply();

                boolean installSuccess = true;

                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onInstallComplete(installSuccess);
                    }
                });
            } catch (Exception e) {
                boolean installSuccess = false;

                e.printStackTrace();
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onInstallComplete(installSuccess);
                    }

                    Toast.makeText(
                            this,
                            "Falha ao baixar client",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }).start();
    }

    public interface ModpackVersionListener {
        void onCheckComplete(boolean success, String newVersion);
    }

    private void checkModpackVersion(ModpackVersionListener listener) {
        new Thread(() -> {
            try {
                ModDetail detail = api.getModDetails(DEFAULT_MODPACK);
                String newVersion = detail.versionNames[0];

                String modpackVersion = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_MODPACK_VERSION, "");
                boolean updated = modpackVersion.equals(newVersion);

                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onCheckComplete(updated, newVersion);
                    }
                });
            } catch (Exception e) {
                boolean updated = false;

                e.printStackTrace();
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onCheckComplete(updated, "");
                    }

                    Toast.makeText(
                            this,
                            "Falha ao checar atualização do modpack",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }).start();
    }

    private void launchGameAfterModpackInstall(String profileName) {
        LauncherProfiles.load();
        MinecraftProfile prof = LauncherProfiles.mainProfileJson != null && LauncherProfiles.mainProfileJson.profiles != null
                ? LauncherProfiles.mainProfileJson.profiles.get(profileName)
                : null;

        if (prof == null || prof.lastVersionId == null || "Unknown".equals(prof.lastVersionId)) {
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return;
        }

        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(
                        LauncherPreferences.PREF_KEY_CURRENT_PROFILE,
                        mSelectedProfile
                )
                .apply();

        String normalizedVersionId = AsyncMinecraftDownloader.normalizeVersionId(prof.lastVersionId);
        JMinecraftVersionList.Version mcVersion = AsyncMinecraftDownloader.getListedVersion(normalizedVersionId);

        new MinecraftDownloader().start(
                this,
                mcVersion,
                normalizedVersionId,
                new ContextAwareDoneListener(this, normalizedVersionId)
        );
    }
}
