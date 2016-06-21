package com.androidadvance.topsnackbar;

/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages {@link TSnackbar}s.
 */
class SnackbarManager {

    private static final int MSG_TIMEOUT = 0;

    private static final int SHORT_DURATION_MS = 1500;
    private static final int LONG_DURATION_MS = 2750;

    private static SnackbarManager instance = new SnackbarManager();
    private final Object mLock;
    private final Handler mHandler;
    private List<SnackbarRecord> mSnackbarsQueue = new ArrayList<>();
    private SnackbarRecord mCurrentSnackbar;
    private boolean mAnySnackBarVisible;

    public static SnackbarManager getInstance() {
        return instance;
    }

    private SnackbarManager() {
        mLock = new Object();
        mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                switch (message.what) {
                    case MSG_TIMEOUT:
                        handleTimeout((SnackbarRecord) message.obj);
                        return true;
                }
                return false;
            }
        });
    }

    public void show(int duration, Callback callback) {
        boolean isAnySnackBarVisible;
        synchronized (mLock) {
            isAnySnackBarVisible = mAnySnackBarVisible;
        }
        SnackbarRecord record = new SnackbarRecord(duration, callback);
        if (isAnySnackBarVisible) {
            mSnackbarsQueue.add(record);
        } else {
            showSnackbar(record);
        }
    }

    private void showSnackbar(SnackbarRecord record) {
        final Callback callback = record.callback.get();
        if (callback != null) {
            mAnySnackBarVisible = true;
            mCurrentSnackbar = record;
            callback.show();
        }
    }

    public void onShown(Callback callback) {
        synchronized (mLock) {
            if (isCurrentSnackbar(callback)) {
                scheduleTimeoutLocked(mCurrentSnackbar);
            }
        }
    }

    public void dismiss(Callback callback, int event) {
        synchronized (mLock) {
            if (isCurrentSnackbar(callback)) {
                cancelSnackbar(mCurrentSnackbar, event);
            } else {
                Iterator<SnackbarRecord> queueIterator = mSnackbarsQueue.iterator();
                while (queueIterator.hasNext()) {
                    SnackbarRecord record = queueIterator.next();
                    if (record.isSnackbar(callback)) {
                        queueIterator.remove();
                        cancelSnackbar(record, event);
                    }
                }
            }
        }
    }

    private boolean cancelSnackbar(SnackbarRecord record, int event) {
        final Callback callback = record.callback.get();
        if (callback != null) {
            if (isCurrentSnackbar(callback)) {
                mAnySnackBarVisible = false;
            }
            callback.dismiss(event);
            return true;
        }
        return false;
    }

    public void onDismissed(Callback callback) {
        synchronized (mLock) {
            if (isCurrentSnackbar(callback)) {
                // If the callback is from a TSnackbar currently show, remove it and show a new one
                mCurrentSnackbar = null;
            }
        }
        checkForMoreSnackbars();
    }

    private boolean isCurrentSnackbar(Callback callback) {
        return mCurrentSnackbar != null && mCurrentSnackbar.isSnackbar(callback);
    }

    private void checkForMoreSnackbars() {
        synchronized (mLock) {
            if (mSnackbarsQueue.size() > 0) {
                SnackbarRecord record = mSnackbarsQueue.remove(0);
                if (null != record && null != record.callback) {
                    show(record.duration, record.callback.get());
                }
            }
        }
    }

    public boolean isCurrent(Callback callback) {
        synchronized (mLock) {
            return isCurrentSnackbar(callback);
        }
    }

    public boolean isCurrentOrNext(Callback callback) {
        synchronized (mLock) {
            return isCurrentSnackbar(callback) || isQueued(callback);
        }
    }

    private boolean isQueued(Callback callback) {
        Iterator<SnackbarRecord> queueIterator = mSnackbarsQueue.iterator();
        while (queueIterator.hasNext()) {
            SnackbarRecord record = queueIterator.next();
            if (record.isSnackbar(callback)) {
                return true;
            }
        }
        return false;
    }

    public void cancelTimeout(Callback callback) {
        synchronized (mLock) {
            if (isCurrentSnackbar(callback)) {
                mHandler.removeCallbacksAndMessages(mCurrentSnackbar);
            }
        }
    }

    public void restoreTimeout(Callback callback) {
        synchronized (mLock) {
            if (isCurrentSnackbar(callback)) {
                scheduleTimeoutLocked(mCurrentSnackbar);
            }
        }
    }

    interface Callback {
        void show();

        void dismiss(int event);
    }

    private static class SnackbarRecord {
        private final WeakReference<Callback> callback;
        private int duration;

        SnackbarRecord(int duration, Callback callback) {
            this.callback = new WeakReference<>(callback);
            this.duration = duration;
        }

        boolean isSnackbar(Callback callback) {
            return callback != null && this.callback.get() == callback;
        }
    }

    private void scheduleTimeoutLocked(SnackbarRecord r) {
        if (r.duration == TSnackbar.LENGTH_INDEFINITE) {
            // If we're set to indefinite, we don't want to set a timeout
            return;
        }

        int durationMs = LONG_DURATION_MS;
        if (r.duration > 0) {
            durationMs = r.duration;
        } else if (r.duration == TSnackbar.LENGTH_SHORT) {
            durationMs = SHORT_DURATION_MS;
        }
        mHandler.removeCallbacksAndMessages(r);
        mHandler.sendMessageDelayed(Message.obtain(mHandler, MSG_TIMEOUT, r), durationMs);
    }

    private void handleTimeout(SnackbarRecord record) {
        synchronized (mLock) {
            cancelSnackbar(record, TSnackbar.Callback.DISMISS_EVENT_TIMEOUT);
        }
    }
}
