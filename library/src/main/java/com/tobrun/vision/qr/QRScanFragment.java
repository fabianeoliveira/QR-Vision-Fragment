package com.tobrun.vision.qr;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;

public class QRScanFragment extends Fragment {

    private CameraSource mCameraSource;
    private CameraSourcePreview mPreview;
    private OnScanCompleteListener mCallback;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof OnScanCompleteListener) {
            mCallback = (OnScanCompleteListener) activity;
        } else {
            throw new ClassCastException("Activity hosting the QRScanFragment must implement the OnScanCompleteListener interface");
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_barcode, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPreview = (CameraSourcePreview) view.findViewById(R.id.preview);
        onCreateDetector(view);
    }

    private void onCreateDetector(@NonNull final View view) {
        final Context context = view.getContext().getApplicationContext();
        final BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        barcodeDetector.setProcessor(new MultiProcessor.Builder<>(new MultiProcessor.Factory<Barcode>() {
            @Override
            public Tracker<Barcode> create(final Barcode barcode) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onScanComplete(barcode.displayValue);
                        mPreview.stop();
                    }
                });
                return new Tracker<>();
            }
        }).build());

        if (!barcodeDetector.isOperational()) {
            mCallback.onCameraError();
            return;
        }

        final ViewTreeObserver observer = view.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    view.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }

                CameraSource.Builder builder = new CameraSource.Builder(context, barcodeDetector)
                        .setFacing(CameraSource.CAMERA_FACING_BACK)
                        .setRequestedPreviewSize(view.getMeasuredWidth(), view.getMeasuredHeight())
                        .setRequestedFps(30.0f);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    builder = builder.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                }

                mCameraSource = builder.build();

                startCameraSource();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        startCameraSource();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mPreview != null) {
            mPreview.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPreview != null) {
            mPreview.release();
        }
    }

    private void startCameraSource() {
        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource);
            } catch (IOException e) {
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    public interface OnScanCompleteListener {
        @UiThread
        void onScanComplete(@NonNull final String qrCode);

        @UiThread
        void onCameraError();
    }
}