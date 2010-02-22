/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.ase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.ase.interpreter.Interpreter;
import com.google.ase.interpreter.InterpreterUtils;
import com.google.ase.facade.AndroidFacade;
import com.google.ase.facade.MediaFacade;
import com.google.ase.facade.SpeechRecognitionFacade;
import com.google.ase.facade.TextToSpeechFacade;
import com.google.ase.facade.UiFacade;
import com.google.ase.jsonrpc.JsonRpcServer;
import com.google.ase.jsonrpc.RpcInfo;

public class ApiBrowser extends ListActivity {

  private List<RpcInfo> mRpcInfoList;
  private Set<Integer> mExpandedPositions;
  private ApiBrowserAdapter mAdapter;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    setContentView(R.layout.list);
    CustomWindowTitle.buildWindowTitle(this);
    mExpandedPositions = new HashSet<Integer>();
    mRpcInfoList = buildRpcInfoList();
    mAdapter = new ApiBrowserAdapter();
    setListAdapter(mAdapter);
    AseAnalytics.trackActivity(this);
    setResult(RESULT_CANCELED);
  }

  private List<RpcInfo> buildRpcInfoList() {
    List<RpcInfo> list = new ArrayList<RpcInfo>();
    // TODO(damonkohler): Factor out this list of facades so that it's not duplicated between here
    // and the AndroidProxy.
    list.addAll(JsonRpcServer.buildRpcInfoMap(AndroidFacade.class).values());
    list.addAll(JsonRpcServer.buildRpcInfoMap(MediaFacade.class).values());
    list.addAll(JsonRpcServer.buildRpcInfoMap(SpeechRecognitionFacade.class).values());
    list.addAll(JsonRpcServer.buildRpcInfoMap(TextToSpeechFacade.class).values());
    list.addAll(JsonRpcServer.buildRpcInfoMap(UiFacade.class).values());
    Collections.sort(list, new Comparator<RpcInfo>() {
      public int compare(RpcInfo info1, RpcInfo info2) {
        return info1.getName().compareTo(info2.getName());
      }
    });
    return list;
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    if (mExpandedPositions.contains(position)) {
      mExpandedPositions.remove(position);
    } else {
      mExpandedPositions.add(position);
    }
    mAdapter.notifyDataSetInvalidated();
  }
  
  protected boolean onListItemLongClick(View v, int position) {
    String scriptText = getIntent().getStringExtra(Constants.EXTRA_SCRIPT_TEXT);
    Interpreter interpreter = InterpreterUtils.getInterpreterByName(
        getIntent().getStringExtra(Constants.EXTRA_INTERPRETER_NAME));
    String rpcHelpText = interpreter.getRpcText(scriptText, mRpcInfoList.get(position));

    Intent intent = new Intent();
    intent.putExtra(Constants.EXTRA_RPC_HELP_TEXT, rpcHelpText);
    setResult(RESULT_OK, intent);
    finish();
    return true;
  }

  private class ApiBrowserAdapter extends BaseAdapter {

    @Override
    public int getCount() {
      return mRpcInfoList.size();
    }

    @Override
    public Object getItem(int position) {
      return mRpcInfoList.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
      TextView view = new TextView(ApiBrowser.this);
      view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
      view.setLongClickable(true);
      view.setOnLongClickListener(new OnLongClickListener() {
        
        @Override
        public boolean onLongClick(View v) {
          return onListItemLongClick(v, position);
        }
      });
      if (mExpandedPositions.contains(position)) {
        view.setText(mRpcInfoList.get(position).getHelp());
      } else {
        view.setText(mRpcInfoList.get(position).getName());
      }
      return view;
    }
  }
}
