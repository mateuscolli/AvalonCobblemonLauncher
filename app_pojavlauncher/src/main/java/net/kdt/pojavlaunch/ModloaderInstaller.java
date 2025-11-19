package net.kdt.pojavlaunch;

import android.content.Context;

import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.fragments.ForgeInstallFragment;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy;

import java.io.File;

public class ModloaderInstaller implements ModloaderDownloadListener {
    public static final String TAG_FORGE = "ForgeInstallFragment";
    public static final String TAG_FABRIC = "FabricInstallFragment";
    public static final String TAG_OPTIFINE = "OptiFineInstallFragment";
    private final String extraTag;
    private final LoaderType loaderType;
    private final Context context;

    public ModloaderInstaller(Context context, LoaderType loaderType) {
        this.loaderType = loaderType;
        this.context = context;

        if(loaderType == LoaderType.FORGE) {
            this.extraTag = TAG_FORGE;
            return;
        }
        if(loaderType == LoaderType.FABRIC) {
            this.extraTag = TAG_FABRIC;
            return;
        }

        this.extraTag = TAG_OPTIFINE;
    }

    public enum LoaderType {
        FORGE,
        FABRIC,
        OPTIFINE
    }

    public void installLoader(String mcVersion, String loaderVersion) {
        switch (this.loaderType) {
            case FORGE:
                installLoaderForge(mcVersion+"-"+loaderVersion);
                break;
            case FABRIC:
                installLoaderFabric(mcVersion+"-"+loaderVersion);
                break;
            case OPTIFINE:
            default:
                installLoaderOptiFine(mcVersion+"-"+loaderVersion);
                break;
        }
    }

    public void installLoaderForge(String forgeVersion) {
        ModloaderListenerProxy taskProxy = new ModloaderListenerProxy();
        Runnable downloadTask = new ForgeInstallFragment().createDownloadTask(forgeVersion, taskProxy);
        setTaskProxy(taskProxy);
        taskProxy.attachListener(this);
        new Thread(downloadTask).start();
    }

    public void installLoaderFabric(String forgeVersion) {
        ModloaderListenerProxy taskProxy = new ModloaderListenerProxy();
        Runnable downloadTask = new ForgeInstallFragment().createDownloadTask(forgeVersion, taskProxy);
        setTaskProxy(taskProxy);
        taskProxy.attachListener(this);
        new Thread(downloadTask).start();
    }

    public void installLoaderOptiFine(String forgeVersion) {
        ModloaderListenerProxy taskProxy = new ModloaderListenerProxy();
        Runnable downloadTask = new ForgeInstallFragment().createDownloadTask(forgeVersion, taskProxy);
        setTaskProxy(taskProxy);
        taskProxy.attachListener(this);
        new Thread(downloadTask).start();
    }

    private void setTaskProxy(ModloaderListenerProxy proxy) {
        ExtraCore.setValue(this.extraTag, proxy);
    }

    @Override
    public void onDownloadFinished(File downloadedFile) {
        Tools.runOnUiThread(()->{
            getTaskProxy().detachListener();
            setTaskProxy(null);
            new ForgeInstallFragment().onDownloadFinished(this.context, downloadedFile);
        });
    }

    @Override
    public void onDataNotAvailable() {
        Tools.runOnUiThread(()->{
            getTaskProxy().detachListener();
            setTaskProxy(null);
            Tools.dialog(this.context,
                    context.getString(R.string.global_error),
                    this.context.getString(new ForgeInstallFragment().getNoDataMsg()));
        });
    }

    @Override
    public void onDownloadError(Exception e) {
        Tools.runOnUiThread(()->{
            getTaskProxy().detachListener();
            setTaskProxy(null);
            Tools.showError(this.context, e);
        });
    }

    private ModloaderListenerProxy getTaskProxy() {
        return (ModloaderListenerProxy) ExtraCore.getValue(this.extraTag);
    }
}
