package com.qualcomm.qti.setuptemp.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.provision.R;

public class ListViewAdapter extends BaseAdapter {

	private Context context;
	LayoutInflater inflater;
	private List<String> mDatas = new ArrayList<>();
	//记录所有radiobutton被checked的状态
	private HashMap<Integer, Boolean> states = new HashMap<Integer, Boolean>();

	public ListViewAdapter(Context context) {
		this.context = context;
		inflater = LayoutInflater.from(this.context);
	}

	public void setDatas(List<String> datas) {
		mDatas.addAll(datas);
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return mDatas.size();
	}

	@Override
	public Object getItem(int position) {
		return mDatas.get(position);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder = null;
		if (convertView == null) {
			convertView = inflater.inflate(R.layout.item_listview, null);
			holder = new ViewHolder();
			holder.mTxt = (TextView) convertView.findViewById(R.id.tv_detail);
			holder.mRadioButton = (RadioButton) convertView.findViewById(R.id.rb_status);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}
		holder.mTxt.setText(mDatas.get(position));

		boolean res = false; //判断当前位置的radiobutton点击状态
		if (getStates(position) == null || !getStates(position))
		{
			res = false;
			setStates(position, false);
		} else {
			res = true;
		}
		holder.mRadioButton.setChecked(res);
		return convertView;
	}

    // 重置，确保最多只有一项被选中
	public void resetStates(int position) {
		for (Integer key : states.keySet()) {
			states.put(key, false);
		}
		states.put(position, true);
	}

	//用于获取状态值
	public Boolean getStates(int position) {
		return states.get(position);
	}

    //设置状态值
	public void setStates(int position, boolean isChecked) {
		states.put(position, isChecked);
	}

	class ViewHolder {
		TextView mTxt;
		RadioButton mRadioButton;
	}

}
