/*
 * Copyright 2014 ParanoidAndroid Project
 * Copyright 2015 AICP Project
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

package com.aicp.aicpota.cards;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.aicp.aicpota.IOUtils;
import com.aicp.aicpota.R;
import com.aicp.aicpota.Utils;
import com.aicp.aicpota.activities.RequestFileActivity;
import com.aicp.aicpota.activities.RequestFileActivity.RequestFileCallback;
import com.aicp.aicpota.helpers.RebootHelper;
import com.aicp.aicpota.widget.Card;
import com.aicp.aicpota.widget.Item;
import com.aicp.aicpota.widget.Item.OnItemClickListener;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class InstallCard extends Card implements RequestFileCallback {

    private static final String FILES = "FILES";

    private final RebootHelper mRebootHelper;
    private final List<File> mFiles = new ArrayList<>();
    private final LinearLayout mLayout;
    private final Item mAdd;
    private final Item mInstall;
    private final CheckBox mBackup;
    private final CheckBox mWipeData;
    private final CheckBox mWipeCaches;
    private final View mAdditional;

    public InstallCard(Context context, RebootHelper rebootHelper,
                       Bundle savedInstanceState) {
        super(context, savedInstanceState);

        setTitle(R.string.install_title);
        setLayoutId(R.layout.card_install);

        mRebootHelper = rebootHelper;

        mLayout = (LinearLayout) findLayoutViewById(R.id.layout);
        mAdd = (Item) findLayoutViewById(R.id.add);
        mInstall = (Item) findLayoutViewById(R.id.install);
        mBackup = (CheckBox) findLayoutViewById(R.id.backup);
        mWipeData = (CheckBox) findLayoutViewById(R.id.wipedata);
        mWipeCaches = (CheckBox) findLayoutViewById(R.id.wipecaches);
        mAdditional = findLayoutViewById(R.id.additional);

        mInstall.setEnabled(false);

        mAdd.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onClick() {
                Context context = getContext();
                Intent intent = new Intent(context, RequestFileActivity.class);
                context.startActivity(intent);
            }

        });

        mInstall.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onClick() {
                String[] items = new String[mFiles.size()];
                for (int i = 0; i < mFiles.size(); i++) {
                    File file = mFiles.get(i);
                    items[i] = file.getAbsolutePath();
                }
                mRebootHelper.showRebootDialog(getContext(), items, mBackup.isChecked(),
                        mWipeData.isChecked(), mWipeCaches.isChecked());
            }

        });

        RequestFileActivity.setRequestFileCallback(this);

        if (savedInstanceState != null) {
            List<File> files = (List<File>) savedInstanceState.getSerializable(FILES);
            if (files != null) {
                for (File file : files) {
                    addFile(file, null);
                }
            }
        }

        if (isExpanded()) {
            mAdditional.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void expand() {
        super.expand();
        if (mAdditional != null) {
            mAdditional.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void collapse() {
        super.collapse();
        mAdditional.setVisibility(View.GONE);
    }

    @Override
    public void saveState(Bundle outState) {
        super.saveState(outState);
        outState.putSerializable(FILES, (Serializable) mFiles);
    }

    @Override
    public void fileRequested(String filePath) {
        if (filePath == null) {
            Utils.showToastOnUiThread(getContext(), R.string.file_not_found);
        } else {
            addFile(new File(filePath), null);
        }
    }

    public void addFile(Uri uri, final String md5) {
        String filePath = uri.toString().replace("file://", "");
        File file = new File(filePath);
        addFile(file, md5);
    }

    private void addFile(final File file, final String md5) {

        if (md5 != null && !"".equals(md5)) {

            final ProgressDialog pDialog = new ProgressDialog(getContext());
            pDialog.setIndeterminate(true);
            pDialog.setMessage(getResources().getString(R.string.calculating_md5));
            pDialog.setCancelable(false);
            pDialog.setCanceledOnTouchOutside(false);
            pDialog.show();

            (new Thread() {

                public void run() {

                    final String calculatedMd5 = IOUtils.md5(file);

                    pDialog.dismiss();

                    ((Activity) getContext()).runOnUiThread(new Runnable() {

                        public void run() {
                            if (md5.equals(calculatedMd5)) {
                                reallyAddFile(file);
                            } else {
                                showMd5Mismatch(md5, calculatedMd5, file);
                            }
                        }
                    });
                }
            }).start();

        } else {
            reallyAddFile(file);
        }
    }

    private void reallyAddFile(final File file) {
        mFiles.add(file);

        final Item item = new Item(getContext(), null);
        item.setTitle(file.getName());
        item.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onClick() {
                showRemoveDialog(item, file);
            }

        });
        mLayout.addView(item);
        mInstall.setEnabled(true);
    }

    private void showMd5Mismatch(String md5, String calculated, final File file) {
        AlertDialog.Builder alert = new AlertDialog.Builder(getContext());
        alert.setTitle(R.string.md5_mismatch);
        alert.setMessage(getResources().getString(R.string.md5_mismatch_summary,
                new Object[] {
                        md5, calculated
                }));
        alert.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        alert.setNegativeButton(R.string.md5_install_anyway, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();

                reallyAddFile(file);
            }
        });
        alert.show();
    }

    private void showRemoveDialog(final Item item, final File file) {
        Context context = getContext();
        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.remove_file_title);
        String message = context.getResources().getString(R.string.remove_file_summary,
                file.getName());
        alert.setMessage(message);
        alert.setPositiveButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        alert.setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();

                mFiles.remove(file);
                mLayout.removeView(item);
                mInstall.setEnabled(mFiles.size() > 0);
            }
        });
        alert.show();
    }

}
