package com.tplink.cartoon.ui.adapter;
/*
 * Copyright (C), 2018, TP-LINK TECHNOLOGIES CO., LTD.
 *
 * ${FILE_NAME}
 *
 * Description
 *
 * Author xufeng
 *
 * Ver 1.0, 18-8-2, xufeng, Create file
 */

import android.content.Context;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.tplink.cartoon.data.common.Constants;

import java.util.ArrayList;
import java.util.List;

import uk.co.senab.photoview.PhotoView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class ChapterViewpagerAdapter extends PagerAdapter {
    private Context mContext;
    private final ArrayList<String> mDatas;
    private OnceClickListener listener;
    private int mDirection = Constants.LEFT_TO_RIGHT;

    public ChapterViewpagerAdapter(Context context) {
        mDatas = new ArrayList<>();
        mContext = context;
    }

    public int getDirection() {
        return mDirection;
    }

    public void setDirection(int direction) {
        mDirection = direction;
        notifyDataSetChanged();
    }

    public void setDatas(List<String> datas) {
        mDatas.clear();
        mDatas.addAll(datas);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mDatas.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        PhotoView imageView = new PhotoView(mContext);
        if (mDirection == Constants.RIGHT_TO_LEFT) {
            Glide.with(mContext)
                    .load(mDatas.get(mDatas.size() - position - 1))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(imageView);
        } else {
            Glide.with(mContext)
                    .load(mDatas.get(position))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(imageView);
        }
        imageView.setOnPhotoTapListener(new PhotoViewAttacher.OnPhotoTapListener() {
            @Override
            public void onPhotoTap(View view, float x, float y) {
                if (listener != null) {
                    listener.onClick(view, x, y);
                }
            }
        });
        container.addView(imageView);

        return imageView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {//必须实现，销毁
        container.removeView((View) object);
    }

    public void setListener(OnceClickListener listener) {
        this.listener = listener;
    }

    public interface OnceClickListener{
        void onClick(View view,float x, float y);
    }
}
