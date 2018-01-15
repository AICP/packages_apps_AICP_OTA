/*
 * Copyright 2014 ParanoidAndroid Project
 *
 * This file is part of Paranoid OTA.
 *
 * Paranoid OTA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Paranoid OTA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Paranoid OTA.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.aicp.aicpota.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aicp.aicpota.R;

public class Item extends LinearLayout {

    public interface OnItemClickListener {
        void onClick();
    }

    private final ImageView mIconView;
    private final TextView mTitleView;
    private OnItemClickListener mItemClickListener;

    public Item(final Context context, AttributeSet attrs) {
        super(context, attrs);

        String title = null;
        Drawable icon = null;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Item);

        CharSequence s = a.getString(R.styleable.Item_itemTitle);
        if (s != null) {
            title = s.toString();
        }
        Drawable d = a.getDrawable(R.styleable.Item_itemIcon);
        if (d != null) {
            icon = d;
        }

        a.recycle();

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.item, this, true);

        mTitleView = (TextView) view.findViewById(R.id.title);
        mTitleView.setText(title);

        mIconView = (ImageView) view.findViewById(R.id.icon);
        mIconView.setImageDrawable(icon);

        setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mItemClickListener != null) {
                        mItemClickListener.onClick();
                    }
                }
        });
    }

    public void setOnItemClickListener(OnItemClickListener itemClickListener) {
        mItemClickListener = itemClickListener;
    }

    public void setTitle(int resourceId) {
        mTitleView.setText(resourceId);
    }

    public void setTitle(String text) {
        mTitleView.setText(text);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mIconView != null && mTitleView != null) {
            mIconView.setEnabled(enabled);
            mTitleView.setEnabled(enabled);
        }
    }
}
