package com.qualcomm.qti.setuptemp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.List;

public abstract class CommonAdapter<DataType,VH> extends BaseAdapter {
	private List<DataType> mDatas;
	protected Context mContext;

	public CommonAdapter(Context context, List<DataType> datas) {
		mDatas = datas;
		mContext = context;
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
		VH holder;

		int type = getItemViewType(position);
		if (convertView == null) {
			convertView = LayoutInflater.from(parent.getContext()).inflate(getItemLayoutResId(position,type), parent, false);
			holder = (VH) onCreateViewHolder(convertView,position,type);
			convertView.setTag(holder);
		} else {
			holder = (VH) convertView.getTag();
		}

		DataType item = mDatas.get(position);
		onBindViewHolder(holder, item, position,type);

		return convertView;
	}

	protected abstract VH onCreateViewHolder(View itemView, int position, int type);

	protected abstract void onBindViewHolder(VH holder, DataType item, int position, int type);

	protected abstract int getItemLayoutResId(int position, int type);

}
