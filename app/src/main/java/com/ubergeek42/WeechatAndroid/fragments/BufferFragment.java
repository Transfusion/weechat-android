package com.ubergeek42.WeechatAndroid.fragments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.annotation.SuppressLint;
import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.WeechatAndroid.copypaste.Paste;
import com.ubergeek42.WeechatAndroid.tabcomplete.OnlineTabCompleter;
import com.ubergeek42.WeechatAndroid.tabcomplete.TabCompleter;
import com.ubergeek42.WeechatAndroid.upload.Config;
import com.ubergeek42.WeechatAndroid.upload.FileChooserKt;
import com.ubergeek42.WeechatAndroid.upload.InsertAt;
import com.ubergeek42.WeechatAndroid.upload.Target;
import com.ubergeek42.WeechatAndroid.upload.Upload;
import com.ubergeek42.WeechatAndroid.upload.UploadConfigKt;
import com.ubergeek42.WeechatAndroid.upload.UploadObserver;
import com.ubergeek42.WeechatAndroid.upload.UploadManager;
import com.ubergeek42.WeechatAndroid.upload.MediaAcceptingEditText;
import com.ubergeek42.WeechatAndroid.upload.ShareObject;
import com.ubergeek42.WeechatAndroid.upload.Suri;
import com.ubergeek42.WeechatAndroid.utils.AnimatedRecyclerView;
import com.ubergeek42.WeechatAndroid.adapters.ChatLinesAdapter;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferEye;
import com.ubergeek42.WeechatAndroid.relay.BufferList;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.FriendlyExceptions;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.cats.Cat;
import com.ubergeek42.cats.CatD;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;
import com.ubergeek42.weechat.ColorScheme;

import static android.app.Activity.RESULT_OK;
import static com.ubergeek42.WeechatAndroid.service.Events.*;
import static com.ubergeek42.WeechatAndroid.service.RelayService.STATE.*;
import static com.ubergeek42.WeechatAndroid.upload.FileChooserKt.WRITE_PERMISSION_REQUEST_FOR_CAMERA;
import static com.ubergeek42.WeechatAndroid.upload.FileChooserKt.chooseFiles;
import static com.ubergeek42.WeechatAndroid.utils.Toaster.ErrorToast;


public class BufferFragment extends Fragment implements BufferEye, OnKeyListener,
        OnClickListener, TextWatcher, TextView.OnEditorActionListener {

    final private @Root Kitty kitty = Kitty.make("BF");

    private final static String POINTER = "pointer";

    private WeechatActivity activity = null;
    private boolean attached = false;

    private AnimatedRecyclerView uiLines;
    private MediaAcceptingEditText uiInput;
    private ImageButton uiPaperclip;
    private ImageButton uiSend;
    private ImageButton uiTab;

    private ViewGroup uploadLayout;
    private ProgressBar uploadProgressBar;
    private ImageButton uploadButton;

    private long pointer = 0;
    private Buffer buffer;

    private ChatLinesAdapter linesAdapter;

    public static BufferFragment newInstance(long pointer) {
        BufferFragment fragment = new BufferFragment();
        Bundle args = new Bundle();
        args.putLong(POINTER, pointer);
        fragment.setArguments(args);
        return fragment;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// life cycle
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("ConstantConditions")
    @MainThread @Override public void setArguments(@Nullable Bundle args) {
        super.setArguments(args);
        pointer = getArguments().getLong(POINTER);
        kitty.setPrefix(Utils.pointerToString(pointer));
        uploadManager = UploadManager.forBuffer(pointer);

    }

    @MainThread @Override @Cat public void onAttach(Context context) {
        super.onAttach(context);
        this.activity = (WeechatActivity) context;
    }

    @MainThread @Override @Cat public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @MainThread @Override @Cat
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.chatview_main, container, false);

        uiLines = v.findViewById(R.id.chatview_lines);
        uiInput = v.findViewById(R.id.chatview_input);
        uiPaperclip = v.findViewById(R.id.chatview_paperclip);
        uiSend = v.findViewById(R.id.chatview_send);
        uiTab = v.findViewById(R.id.chatview_tab);

        uploadLayout = v.findViewById(R.id.upload_layout);
        uploadProgressBar = v.findViewById(R.id.upload_progress_bar);
        uploadButton = v.findViewById(R.id.upload_button);
        uploadButton.setOnClickListener(vv -> {
            if (lastUploadStatus == UploadStatus.UPLOADING) {
                uploadManager.filterUploads(Collections.emptyList());
            } else {
                startUploads(uiInput.getNotReadySuris());
            }
        });


        linesAdapter = new ChatLinesAdapter(uiLines);
        uiLines.setAdapter(linesAdapter);
        uiLines.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (focused && dy != 0) activity.toolbarController.onScroll(dy, uiLines.getOnTop(), uiLines.getOnBottom());
            }
        });

        uiPaperclip.setOnClickListener((View view) -> chooseFiles(this, Config.paperclipAction1));
        uiPaperclip.setOnLongClickListener((View view) -> {
            if (Config.paperclipAction2 != null) {
                chooseFiles(this, Config.paperclipAction2);
                return true;
            }
            return false;
        });

        uiSend.setOnClickListener(this);
        uiTab.setOnClickListener(this);
        uiInput.setOnKeyListener(this);            // listen for hardware keyboard
        uiInput.addTextChangedListener(this);      // listen for software keyboard through watching input box text
        uiInput.setOnEditorActionListener(this);   // listen for software keyboard's “send” click. see onEditorAction()

        uiLines.setFocusable(false);
        uiLines.setFocusableInTouchMode(false);

        uiInput.setOnLongClickListener((View view) -> Paste.showPasteDialog(uiInput));

        online = true;
        return v;
    }

    @MainThread @Override @Cat public void onDestroyView() {
        super.onDestroyView();
        uiLines = null;
        uiInput = null;
        uiSend = null;
        uiTab = null;
        uiPaperclip = null;
        uploadLayout = null;
        uploadButton = null;
        uploadProgressBar = null;
        linesAdapter = null;
    }

    @MainThread @Override @Cat public void onResume() {
        super.onResume();
        uiTab.setVisibility(P.showTab ? View.VISIBLE : View.GONE);
        uiPaperclip.setVisibility(P.showPaperclip ? View.VISIBLE : View.GONE);
        uiLines.setBackgroundColor(0xFF000000 | ColorScheme.get().default_color[ColorScheme.OPT_BG]);
        EventBus.getDefault().register(this);
        applyColorSchemeToViews();

        uiInput.textifyReadySuris();                // this will fix any uploads that were finished while we were absent
        afterTextChanged2();                        // this will set appropriate upload ui state
        showHidePaperclip();
        uploadManager.setObserver(uploadObserver);  // this will resume ui for any uploads that are still running
    }

    @MainThread @Override @Cat public void onPause() {
        super.onPause();
        uploadManager.setObserver(null);
        lastUploadStatus = null; // setObserver & afterTextChanged2 will fix this
        detachFromBuffer();
        EventBus.getDefault().unregister(this);
    }

    @MainThread @Override @Cat public void onDetach() {
        activity = null;
        super.onDetach();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public enum State {ATTACHMENT, PAGER_FOCUS, FULL_VISIBILITY, LINES}
    @MainThread @Cat(linger=true) public void onVisibilityStateChanged(State state) {
        if (activity == null || buffer == null || !buffer.linesAreReady()) return;
        kitty.trace("proceeding!");

        boolean watched = attached && focused && !activity.isPagerNoticeablyObscured();
        if (buffer.isWatched != watched) {
            if (watched) linesAdapter.scrollToHotLineIfNeeded();
            buffer.setWatched(watched);
        }

        if ((state == State.PAGER_FOCUS && !focused) ||                 // swiping left/right or
            (state == State.ATTACHMENT && !attached && focused)) {      // minimizing app, closing buffer, disconnecting
            buffer.moveReadMarkerToEnd();
            if (state == State.PAGER_FOCUS) linesAdapter.onLineAdded();
        }
    }

    private boolean focused = false;

    // called by MainPagerAdapter
    // if true the page is the main in the adapter; called when sideways scrolling is complete
    @MainThread @Override public void setUserVisibleHint(boolean focused) {
        super.setUserVisibleHint(focused);
        this.focused = focused;
        onVisibilityStateChanged(State.PAGER_FOCUS);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// events
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean online = true;

    // this can be forced to always run in background, but then it would run after onStart()
    // if the fragment hasn't been initialized yet, that would lead to a bit of flicker
    @Subscribe(sticky=true, threadMode=ThreadMode.MAIN)
    @MainThread @Cat public void onEvent(@NonNull StateChangedEvent event) {
        boolean online = event.state.contains(LISTED);
        if (buffer == null || online) {
            buffer = BufferList.findByPointer(pointer);
            if (online && buffer == null) {
                onBufferClosed();
                return;
            }
            if (buffer != null) attachToBuffer();
            else kitty.warn("...buffer is null");    // this should only happen after OOM kill
        }
        if (this.online != (this.online = online)) initUI();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// attach detach

    @MainThread @Cat private void attachToBuffer() {
        buffer.setBufferEye(this);
        linesAdapter.setBuffer(buffer);
        linesAdapter.loadLinesWithoutAnimation();
        attached = true;
        onVisibilityStateChanged(State.ATTACHMENT);
    }

    // buffer might be null if we are closing fragment that is not connected
    @MainThread @Cat private void detachFromBuffer() {
        attached = false;
        onVisibilityStateChanged(State.ATTACHMENT);
        linesAdapter.setBuffer(null);
        if (buffer != null) buffer.setBufferEye(null);
        buffer = null;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// ui

    @MainThread @Cat private void initUI() {
        uiInput.setEnabled(online);
        uiSend.setEnabled(online);
        uiTab.setEnabled(online);
        uiPaperclip.setEnabled(online);
        if (!online) activity.hideSoftwareKeyboard();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferEye stuff
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @WorkerThread @Override public void onLineAdded() {
        ChatLinesAdapter a = linesAdapter; if (a != null) a.onLineAdded();
    }

    @MainThread @Override public void onGlobalPreferencesChanged(boolean numberChanged) {
        if (linesAdapter == null) return;
        linesAdapter.onGlobalPreferencesChanged(numberChanged);
    }

    @WorkerThread @Override public void onLinesListed() {
        if (uiLines != null) uiLines.requestAnimation();
        ChatLinesAdapter a = linesAdapter; if (a != null) a.onLinesListed();
        Weechat.runOnMainThread(() -> onVisibilityStateChanged(State.LINES));
    }

    @WorkerThread @Override public void onPropertiesChanged() {
        ChatLinesAdapter a = linesAdapter; if (a != null) a.onPropertiesChanged();
    }

    @AnyThread @Override public void onBufferClosed() {
        Weechat.runOnMainThreadASAP(() -> {if (activity != null) activity.closeBuffer(pointer);});
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// keyboard / buttons
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // the only OnKeyListener's method, only applies to hardware buttons
    // User pressed some key in the input box, check for what it was
    @MainThread @Override public boolean onKey(View v, int keycode, KeyEvent event) {
        int action = event.getAction();
        return checkSendMessage(keycode, action) ||
                checkVolumeButtonResize(keycode, action) ||
                checkForTabCompletion(keycode, action);

    }

    @MainThread private boolean checkSendMessage(int keycode, int action) {
        if (keycode == KeyEvent.KEYCODE_ENTER) {
            if (action == KeyEvent.ACTION_UP) sendMessage();
            return true;
        }
        return false;
    }

    @MainThread private boolean checkForTabCompletion(int keycode, int action) {
        if ((keycode == KeyEvent.KEYCODE_TAB || keycode == KeyEvent.KEYCODE_SEARCH) &&
                action == KeyEvent.ACTION_DOWN) {
            tryTabComplete();
            return true;
        }
        return false;
    }

    @MainThread private boolean checkVolumeButtonResize(int keycode, int action) {
        if (keycode == KeyEvent.KEYCODE_VOLUME_DOWN || keycode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (P.volumeBtnSize) {
                if (action == KeyEvent.ACTION_UP) {
                    float textSize = P.textSize;
                    switch (keycode) {
                        case KeyEvent.KEYCODE_VOLUME_UP:
                            if (textSize < 30) textSize += 1;
                            break;
                        case KeyEvent.KEYCODE_VOLUME_DOWN:
                            if (textSize > 5) textSize -= 1;
                            break;
                    }
                    P.setTextSizeColorAndLetterWidth(textSize);
                }
                return true;
            }
        }
        return false;
    }

    // the only OnClickListener's method. send button or tab button pressed
    @MainThread @Override public void onClick(View v) {
        switch (v.getId()) {
            case R.id.chatview_send: sendMessage(); break;
            case R.id.chatview_tab: tryTabComplete(); break;
        }
    }

    // the only OnEditorActionListener's method
    // listens to keyboard's “send” press (not our button)
    @MainThread @Override public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            sendMessage();
            return true;
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// send message

    @MainThread private void sendMessage() {
        List<Suri> suris = uiInput.getNotReadySuris();
        if (!(Utils.isEmpty(suris))) {
            startUploads(suris);
            return;
        }
        SendMessageEvent.fireInput(buffer, uiInput.getText().toString());
        uiInput.setText("");   // this will reset tab completion
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// tab completion
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private TabCompleter completer = null;

    @MainThread @SuppressLint("SetTextI18n") private void tryTabComplete() {
        if (buffer == null) return;
        if (completer == null)
            completer = TabCompleter.obtain(getLifecycle(), buffer, uiInput);
        completer.next();
    }

    @MainThread @SuppressLint("SetTextI18n") public void setShareObject(ShareObject shareObject) {
        shareObject.insert(uiInput, InsertAt.END);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////// text watcher

    @MainThread @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @MainThread @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    // invalidate tab completion progress on input box text change
    // tryTabComplete() will set it back if it modified the text causing this function to run
    @MainThread @Override public void afterTextChanged(Editable s) {
        if (completer != null) {
            completer.cancel();
            if (!completer.shouldntNullOut())
                completer = null;
        }
        afterTextChanged2();
        showHidePaperclip();
    }

    @SuppressWarnings("ConstantConditions") private void applyColorSchemeToViews() {
        getView().findViewById(R.id.chatview_bottombar).setBackgroundColor(P.colorPrimary);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    enum UploadStatus {
        NOTHING_TO_UPLOAD,
        HAS_THINGS_TO_UPLOAD,
        UPLOADING
    }

    private UploadStatus lastUploadStatus = null;

    @CatD @MainThread void setUploadStatus(UploadStatus uploadStatus) {
        if (uploadStatus == lastUploadStatus) return;
        lastUploadStatus = uploadStatus;

        switch (uploadStatus) {
            case NOTHING_TO_UPLOAD:
                if (P.showSend) uiSend.setVisibility(View.VISIBLE);
                uploadLayout.setVisibility(View.GONE);
                return;
            case HAS_THINGS_TO_UPLOAD:
                uiSend.setVisibility(View.GONE);
                uploadLayout.setVisibility(View.VISIBLE);
                uploadButton.setImageResource(R.drawable.ic_toolbar_upload);
                setUploadProgress(-1);
                break;
            case UPLOADING:
                uiSend.setVisibility(View.GONE);
                uploadLayout.setVisibility(View.VISIBLE);
                uploadButton.setImageResource(R.drawable.ic_toolbar_upload_cancel);
                setUploadProgress(-1);
                break;
        }
    }

    // show indeterminate progress in the end, when waiting for the server to produce a response
    @CatD @MainThread void setUploadProgress(float ratio) {
        if (ratio < 0) {
            uploadProgressBar.setVisibility(View.INVISIBLE);
            uploadProgressBar.setIndeterminate(false);      // this will reset the thing
            uploadProgressBar.setProgress(1);               // but it has a bug so we need to
            uploadProgressBar.setProgress(0);               // set it twice
        } else {
            uploadProgressBar.setVisibility(View.VISIBLE);
            if (ratio >= 1f) {
                uploadProgressBar.setIndeterminate(true);
            } else {
                uploadProgressBar.setIndeterminate(false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    uploadProgressBar.setProgress((int) (100 * ratio), true);
                } else {
                    uploadProgressBar.setProgress((int) (100 * ratio));
                }
            }
        }
    }

    UploadManager uploadManager;

    void afterTextChanged2() {
        List<Suri> suris = uiInput.getNotReadySuris();

        if (lastUploadStatus == UploadStatus.UPLOADING) {
            uploadManager.filterUploads(suris);
        }

        if (suris.size() == 0) {
            setUploadStatus(UploadStatus.NOTHING_TO_UPLOAD);
        } else if (lastUploadStatus != UploadStatus.UPLOADING) {
            setUploadStatus(UploadStatus.HAS_THINGS_TO_UPLOAD);
        }
    }

    UploadObserver uploadObserver = new UploadObserver() {
        @Override public void onUploadsStarted() {
            setUploadStatus(UploadStatus.UPLOADING);
        }

        @Override public void onProgress(float ratio) {
            setUploadProgress(ratio);
        }

        @Override public void onUploadDone(@NotNull Suri suri) {
            uiInput.textifyReadySuris();
        }

        @Override public void onUploadFailure(@NotNull Suri suri, @NotNull Exception e) {
            if (!(e instanceof Upload.CancelledException)) {
                String message = new FriendlyExceptions(getContext()).getFriendlyException(e).message;
                ErrorToast.show("Could not upload: %s\n\nError: %s", suri.getUri(), message);
            }
        }

        @Override public void onFinished() {
            setUploadStatus(uiInput.getNotReadySuris().size() != 0 ?
                    UploadStatus.HAS_THINGS_TO_UPLOAD : UploadStatus.NOTHING_TO_UPLOAD);
        }
    };

    @Override @Cat public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK) {
            try {
                ShareObject shareObject = FileChooserKt.getShareObjectFromIntent(requestCode, data);
                if (shareObject != null) shareObject.insert(uiInput, InsertAt.CURRENT_POSITION);
            } catch (Exception e) {
                ErrorToast.show(e);
            }
        }
    }


    public boolean shouldShowUploadMenus() {
        return P.showPaperclip && uiPaperclip != null && uiPaperclip.getVisibility() == View.GONE;
    }

    @MainThread void showHidePaperclip() {
        if (!P.showPaperclip || activity == null || uiPaperclip == null) return;

        ViewGroup layout = (ViewGroup) uiPaperclip.getParent();

        // other buttons at this point might not have been measured, so we can't simply get width
        // of the input field; calculate through layout width instead. see R.layout.chatview_main
        final float textWidth = uiInput.getTextWidth();

        if (textWidth < 0) {
            // rerun when we can determine text width. we can post() but this ensures the code
            // is re-run earlier, and we can actually show-hide paperclip before the first draw
            uiInput.setHasLayoutListener(this::showHidePaperclip);
            return;
        }

        // for the purpose of the subsequent calculation we pretend that paperclip is shown,
        // else ratio can jump backwards on character entry, revealing the button again.
        // if the send button is off, adding a ShareSpan can reveal it (well, upload button),
        // but it's not a problem as it can only appear on text addition and disappear on deletion*
        float widgetWidth = P.weaselWidth - P._4dp - actionButtonWidth;
        if (uiTab.getVisibility() != View.GONE) widgetWidth -= actionButtonWidth;
        if (uiSend.getVisibility() != View.GONE) widgetWidth -= actionButtonWidth;

        boolean shouldShowPaperclip = (textWidth / widgetWidth) < 0.8f;
        if (shouldShowPaperclip == (uiPaperclip.getVisibility() == View.VISIBLE)) return;

        // not entirely correct, but good enough; don't animate if invisible
        boolean alreadyDrawn = layout.getWidth() > 0;
        if (alreadyDrawn) TransitionManager.beginDelayedTransition(layout, paperclipTransition);

        uiPaperclip.setVisibility(shouldShowPaperclip ? View.VISIBLE : View.GONE);
        activity.updateMenuItems();
    }

    private final static float actionButtonWidth = 48 * P._1dp;     // as set in ActionButton style
    private final static Transition paperclipTransition = new Fade();
    static {
        paperclipTransition.setDuration(200);
        paperclipTransition.addTarget(R.id.chatview_paperclip);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == WRITE_PERMISSION_REQUEST_FOR_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                chooseFiles(this, Target.Camera);
            }
        }
    }

    void startUploads(List<Suri> suris) {
        try {
            UploadConfigKt.validateUploadConfig();
            uploadManager.startUploads(suris);
        } catch (Exception e) {
            ErrorToast.show(e);
        }
    }
}
