/*
 * Description
 *
 * Author xufeng
 *
 * Ver 1.0, 18-8-23, xufeng, Create file
 */
package com.tplink.cartoon.ui.presenter;

import android.content.Intent;

import com.tplink.cartoon.data.bean.Comic;
import com.tplink.cartoon.data.bean.DBChapters;
import com.tplink.cartoon.data.bean.DBDownloadItem;
import com.tplink.cartoon.data.bean.DownState;
import com.tplink.cartoon.data.common.Constants;
import com.tplink.cartoon.db.DaoHelper;
import com.tplink.cartoon.net.RetryFunction;
import com.tplink.cartoon.ui.activity.DownloadChapterlistActivity;
import com.tplink.cartoon.ui.source.download.DownloadListDataSource;
import com.tplink.cartoon.ui.widget.CustomDialog;
import com.tplink.cartoon.utils.FileUtil;
import com.tplink.cartoon.utils.IntentUtil;
import com.tplink.cartoon.utils.LogUtil;
import com.tplink.cartoon.utils.ShowErrorTextUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.DisposableSubscriber;
import okhttp3.ResponseBody;

import static com.tplink.cartoon.data.bean.DownState.DOWN;
import static com.tplink.cartoon.data.bean.DownState.ERROR;
import static com.tplink.cartoon.data.bean.DownState.NONE;
import static com.tplink.cartoon.data.bean.DownState.PAUSE;
import static com.tplink.cartoon.data.bean.DownState.START;
import static com.tplink.cartoon.data.bean.DownState.STOP;

public class DownloadChapterlistPresenter extends BasePresenter
        <DownloadListDataSource, DownloadChapterlistActivity> {
    private final CompositeDisposable mCompositeDisposable;
    private Comic mComic;
    //从上个页面获取的map
    private HashMap<Integer, Integer> mMap;
    //保存自己选择状态的MAP
    private HashMap<Integer, Integer> selectMap;
    private List<DBDownloadItem> mLists;
    private final DaoHelper mDaoHelper;
    //下载队列
    private LinkedHashMap<String, DownloadComicDisposableObserver> subMap;
    //下载章节数，同时允许存在四个
    private TreeMap<Integer, DBDownloadItem> downloadMap;
    //是否选择了全部
    private boolean isSelectedAll;
    //选择个数
    private int selectedNum;
    private final static int DOWNLOADNUM = 1;
    //已经下载完成的个数
    int downloadedNum = 0;
    /**
     * 0 下载中
     * 1 停止下载
     * 2 下载完成
     */
    public static final int DOWNLOADING = 0;
    public static final int STOP_DOWNLOAD = 1;
    public static final int FINISH = 2;

    public int isAllDownload = DOWNLOADING;
    private boolean isEditing;

    public boolean isEditing() {
        return isEditing;
    }

    public void setEditing(boolean editing) {
        isEditing = editing;
    }

    public DownloadChapterlistPresenter(DownloadChapterlistActivity view, Intent intent) {
        super(view);
        mCompositeDisposable = new CompositeDisposable();
        mComic = (Comic) intent.getSerializableExtra(Constants.COMIC);
        mMap = (HashMap<Integer, Integer>) intent.getSerializableExtra(Constants.COMIC_SELECT_DOWNLOAD);
        mLists = new ArrayList<>();
        mDaoHelper = new DaoHelper<>(view.getApplicationContext());
        subMap = new LinkedHashMap<>();
        selectMap = new HashMap<>();
        downloadMap = new TreeMap<>();
    }

    public Comic getComic() {
        return mComic;
    }

    /**
     * 初始化按照章節下載
     */
    public void initData() {
        downloadedNum = 0;
        mLists = new ArrayList<>();
        DisposableSubscriber<List<DBDownloadItem>> disposableSubscriber =
                mDataSource.getDbDownloadItemFromDBWithInsert(mComic, mMap)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeWith(new DisposableSubscriber<List<DBDownloadItem>>() {
                            @Override
                            public void onNext(List<DBDownloadItem> items) {
                                mLists.addAll(items);
                                mView.fillData(mLists);
                                //初始化选择
                                clearSelect();
                                //判断有多少是之前已经下载过的
                                for (int i = 0; i < items.size(); i++) {
                                    if (items.get(i).getState() == DownState.FINISH) {
                                        downloadedNum++;
                                    }
                                }
                                //判断是否全部下载完了
                                if (downloadedNum == mLists.size()) {
                                    mView.onDownloadFinished();
                                    mComic.setState(DownState.FINISH);
                                } else {
                                    mComic.setState(DOWN);
                                }
                                //如果不是重新选择了下载章节数进去，不需要更新下载时间
                                if (mMap.size() != 0) {
                                    mComic.setDownloadTime(getCurrentTime());
                                }
                                mComic.setDownload_num_finish(downloadedNum);
                                mComic.setDownload_num(items.size());
                            }

                            @Override
                            public void onError(Throwable e) {
                                mView.showErrorView(ShowErrorTextUtil.ShowErrorText(e));
                                LogUtil.e(mComic.getTitle() + "从数据库中拉取本地下载列表数据失败" + e.toString());
                            }

                            @Override
                            public void onComplete() {

                            }
                        });
        mCompositeDisposable.add(disposableSubscriber);
    }

    public void onItemClick(DBDownloadItem info, int position) {
        if (isEditing()) {
            updateToSelected(position);
        } else {
            switch (info.getState()) {
                case NONE:
                    stop(info, position, false);
                    break;
                case START:
                    //mPresenter.startDown(info);
                    break;
                case PAUSE:
                    //mPresenter.startDown(info,position);
                    break;
                case DOWN:
                    stop(info, position, true);
                    break;
                case STOP:
                    ready(info, position);
                    //mPresenter.startDown(info,position);
                    break;
                case ERROR:
                    ready(info, position);
                    break;
                case DownState.FINISH:
                    toComicChapter(info);
                    break;
            }
        }
    }

    public void updateComic() {
        mDaoHelper.update(mComic);
    }

    /**
     * 开始所有下载
     */
    public void startAll() {
        //找出最前面四个可以下载的bean
        for (int i = 0; i < mLists.size(); i++) {
            if (mLists.get(i).getState() == NONE) {
                if (downloadMap.size() < DOWNLOADNUM) {
                    startDown(mLists.get(i), i);
                }
            }
        }
    }

    public void reStartAll() {
        //找出最前面四个可以下载的bean
        for (int i = 0; i < mLists.size(); i++) {
            if (mLists.get(i).getState() != DownState.FINISH) {
                mLists.get(i).setState(NONE);
                if (downloadMap.size() < DOWNLOADNUM) {
                    startDown(mLists.get(i), i);
                } else {
                    mView.getDataFinish();
                }
            }
        }
    }

    /**
     * 暂停某个下载
     *
     * @param info
     * @param position
     * @param isContinue 是否继续下载
     */
    public void stop(DBDownloadItem info, int position, boolean isContinue) {
        if (info == null) return;
        info.setState(STOP);
        if (info.getChaptersUrl() != null && info.getCurrentNum() + 1 < info.getChaptersUrl().size()) {
            String url = info.getChaptersUrl().get(info.getCurrentNum() + 1);
            //中断单张图片的下载
            if (subMap.containsKey(url)) {
                DownloadComicDisposableObserver subscriber = subMap.get(url);
                subscriber.dispose();//解除请求
                subMap.remove(url);
                LogUtil.v(url + ":停止下载");
            }
        }
        //中断整个章节的下载，并且切换章节
        if (downloadMap.containsKey(info.getChapters()) && isContinue) {
            downloadMap.remove(info.getChapters());
            for (int i = 0; i < mLists.size(); i++) {
                if (mLists.get(i).getState() == NONE) {
                    downloadMap.put(mLists.get(i).getChapters(), mLists.get(i));
                    startDown(mLists.get(i), i);
                    break;
                }
            }
        }
        mDaoHelper.update(info);
        mView.updateView(position);
    }

    /**
     * 暂停某个下载
     *
     * @param info
     */
    public void pause(DBDownloadItem info, int position) {
        LogUtil.d("testA", "点击了暂停");
        if (info == null) {
            return;
        }
        info.setState(PAUSE);
        String url = info.getChaptersUrl().get(info.getCurrentNum() + 1);
        if (subMap.containsKey(url)) {
            DownloadComicDisposableObserver subscriber = subMap.get(url);
            subscriber.dispose();//解除请求
            subMap.remove(url);
            LogUtil.v(url + ":暂停下载");
        }
        /*这里需要讲info信息写入到数据中，可自由扩展，用自己项目的数据库*/
        mDaoHelper.update(info);
        mView.updateView(position);

    }

    /**
     * 开始某个下载
     *
     * @param info
     */
    public void startDown(final DBDownloadItem info, final int position) {
        //加入到下载队列中
        downloadMap.put(info.getChapters(), info);
        if (info.getNum() == 0) {
            //修改状态
            info.setState(START);
            mView.updateView(position);

            DisposableSubscriber<DBChapters> disposableSubscriber = mDataSource
                    .getDownloadChaptersList(mComic.getId(), info.getChapters())
                    .retryWhen(new RetryFunction())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeWith(new DisposableSubscriber<DBChapters>() {
                        @Override
                        public void onNext(DBChapters chapters) {
                            if (info.getState() != STOP) {
                                //修改状态
                                info.setState(DOWN);
                                //设置下载地址
                                info.setChapters_url(chapters.getComiclist());
                                info.setNum(mLists.size());
                                info.setCurrentNum(0);
                                //把获取到的下载地址存进数据库
                                mDaoHelper.update(info);
                                mView.updateView(position);
                                if (mLists != null && mLists.size() != 0) {
                                    downloadChapter(info, info.getCurrentNum(), position);
                                }
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            info.setState(ERROR);
                            if (downloadMap.containsKey(info.getChapters())) {
                                downloadMap.remove(info.getChapters());
                            }
                            mView.updateView(position);
                            //寻找下一话并开始下载
                            for (int i = 0; i < mLists.size(); i++) {
                                if (mLists.get(i).getState() == NONE) {
                                    downloadMap.put(mLists.get(i).getChapters(), mLists.get(i));
                                    //开始下载下一话
                                    startDown(mLists.get(i), i);
                                    break;
                                }
                            }
                            LogUtil.e(t.toString());
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
            mCompositeDisposable.add(disposableSubscriber);
        } else {
            //修改状态
            info.setState(DOWN);
            mDaoHelper.update(info);
            mView.updateView(position);
            downloadChapter(info, info.getCurrentNum(), position);
        }
    }

    /**
     * 递归下载每一话的所有图片
     *
     * @param info
     * @param page
     */
    private void downloadChapter(final DBDownloadItem info, final int page, int postion) {
        DownloadComicDisposableObserver observer = new DownloadComicDisposableObserver(info, page, postion);
        Observable<ResponseBody> observable = mDataSource.download(info, page);
        observable
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map(new Function<ResponseBody, DBDownloadItem>() {

                    @Override
                    public DBDownloadItem apply(ResponseBody responseBody) throws Exception {
                        //把图片保存到SD卡
                        FileUtil.saveImgToSdCard(responseBody.byteStream(), FileUtil.SDPATH + FileUtil.COMIC + info.getComic_id() + "/" + info.getChapters() + "/", page + ".png");
                        ArrayList<String> paths = new ArrayList<>();
                        if (info.getChapters_path() != null) {
                            paths = new ArrayList<>(info.getChapters_path());
                        }
                        paths.add(FileUtil.SDPATH + FileUtil.COMIC + info.getComic_id() + "/" + info.getChapters() + "/" + page + ".png");
                        //保存存储位置
                        info.setChaptersPath(paths);
                        return info;
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(observer);
        subMap.put(info.getChaptersUrl().get(page), observer);
    }

    /**
     * 设置为可以下载的等待状态
     *
     * @param items
     * @param position
     */
    public void ready(DBDownloadItem items, int position) {
        if (downloadMap.size() < DOWNLOADNUM) {
            startDown(items, position);
        } else {
            items.setState(PAUSE);
            mDaoHelper.update(items);
            mView.updateView(position);
        }
    }


    /**
     * 暂停所有下载
     */
    public void pauseAll() {
        DisposableSubscriber<Boolean> disposable = mDataSource.updateDownloadItemsList(mLists)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSubscriber<Boolean>() {
                    @Override
                    public void onNext(Boolean aBoolean) {
                        if (aBoolean) {
                            LogUtil.d("所有状态保存在数据库成功");
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        LogUtil.e(t.toString());
                    }

                    @Override
                    public void onComplete() {

                    }
                });
        mCompositeDisposable.add(disposable);
    }

    /**
     * 停止所有下载
     */
    public void stopAll() {
        for (int i = 0; i < mLists.size(); i++) {
            DBDownloadItem items = mLists.get(i);
            if (items.getState() != DownState.FINISH) {
                items.setState(STOP);
                if (items.getState() == DOWN) {
                    stop(items, i, false);
                }
                downloadMap.clear();
            }
        }
        mView.getDataFinish();
    }

    public void toComicChapter(DBDownloadItem info) {
        IntentUtil.toComicChapterForResult(mView, info.getChapters(), mComic);
    }

    @Override
    protected DownloadListDataSource initDataSource() {
        return new DownloadListDataSource(mView);
    }

    public class DownloadComicDisposableObserver extends DisposableObserver<DBDownloadItem> {
        int page;
        DBDownloadItem info;
        int position;

        public DownloadComicDisposableObserver(DBDownloadItem info, int page, int position) {
            this.page = page;
            this.position = position;
            this.info = info;
        }

        @Override
        public void onNext(@NonNull DBDownloadItem dbDownloadItem) {
            info = dbDownloadItem;
            LogUtil.d(page + "/" + info.getNum() + "下载完成");
            //从队列中移除
            if (subMap.containsKey(info.getChapters_url().get(page))) {
                subMap.remove(info.getChapters_url().get(page));
            }
            //写一个递归继续去下载这一话的下一张图片
            if (page < info.getNum() - 1) {
                if (info.getState() == DOWN) {
                    downloadChapter(info, page + 1, position);
                }
            } else {
                //如果这一话下载完成
                downloadedNum++;
                //修改mComic的状态
                mComic.setDownload_num_finish(downloadedNum);
                //修改状态
                info.setState(DownState.FINISH);
                if (downloadMap.containsKey(info.getChapters())) {
                    downloadMap.remove(info.getChapters());
                }
                //遍历去寻找下一话
                for (int i = 0; i < mLists.size(); i++) {
                    if (mLists.get(i).getState() == NONE) {
                        downloadMap.put(mLists.get(i).getChapters(), mLists.get(i));
                        //开始下载下一话
                        startDown(mLists.get(i), i);
                        break;
                    }
                }
                if (downloadedNum == mLists.size()) {
                    mView.showToast(mComic.getTitle() + "下载完成,共下载" + downloadedNum + "话");
                    mView.onDownloadFinished();
                    isAllDownload = FINISH;
                    //修改mComic的状态
                    mComic.setDownload_num_finish(downloadedNum);
                }
            }
            //把已经下载完成的写入
            info.setCurrentNum(page + 1);
            //更新数据库
            mDaoHelper.update(info);
            //为了防止点击了stop之后，仍然刷新UI，即使图片下载已经完成，仍不刷新UI
            if (info.getState() != STOP) {
                mView.updateView(position);
            }

        }


        @Override
        public void onComplete() {

        }

        @Override
        public void onError(@NonNull Throwable e) {
            info.setState(ERROR);
            if (downloadMap.containsKey(info.getChapters())) {
                downloadMap.remove(info.getChapters());
            }
            mView.updateView(position);
            for (int i = 0; i < mLists.size(); i++) {
                if (mLists.get(i).getState() == NONE) {
                    downloadMap.put(mLists.get(i).getChapters(), mLists.get(i));
                    //开始下载下一话
                    startDown(mLists.get(i), i);
                    break;
                }
            }
            LogUtil.e(e.toString());
        }
    }

    public void getResultComic(int resultCode, Intent data) {
        if (resultCode == Constants.OK) {
            Comic comic = (Comic) data.getSerializableExtra(Constants.COMIC);
            this.mComic = comic;
        }
    }

    /**
     * 选择相关方法
     */
    /**
     * 清除map信息
     */
    public void clearSelect() {
        selectedNum = 0;
        isSelectedAll = false;
        for (int i = 0; i < mLists.size(); i++) {
            selectMap.put(i, Constants.CHAPTER_FREE);
        }
    }

    /**
     * 选择或者取消选择
     *
     * @param position
     */
    public void updateToSelected(int position) {
        if (selectMap.get(position) != null && selectMap.get(position).equals(Constants.CHAPTER_FREE)) {
            selectedNum++;
            selectMap.put(position, Constants.CHAPTER_SELECTED);
            if (selectedNum == mLists.size()) {
                mView.addAll();
                isSelectedAll = true;
            }
        } else if (selectMap.get(position) != null && selectMap.get(position).equals(Constants.CHAPTER_SELECTED)) {
            selectMap.put(position, Constants.CHAPTER_FREE);
            selectedNum--;
            isSelectedAll = false;
            mView.removeAll();
        }
        mView.updateListItem(selectMap, position);
    }

    /**
     * 选择或者移除全部
     */
    public void selectOrMoveAll() {
        if (!isSelectedAll) {
            if (mLists != null && mLists.size() != 0) {
                for (int i = 0; i < mLists.size(); i++) {
                    if (selectMap.get(i) == Constants.CHAPTER_FREE) {
                        selectMap.put(i, Constants.CHAPTER_SELECTED);
                        selectedNum++;
                    }
                }
                mView.addAll();
            }
        } else {
            if (mLists != null && mLists.size() != 0) {
                for (int i = 0; i < mLists.size(); i++) {
                    if (selectMap.get(i) == Constants.CHAPTER_SELECTED) {
                        selectMap.put(i, Constants.CHAPTER_FREE);
                    }
                }
                selectedNum = 0;
                mView.removeAll();
            }
        }
        isSelectedAll = !isSelectedAll;
        mView.updateList(selectMap);
    }

    public void showDeteleDialog() {
        if (selectedNum > 0) {
            final CustomDialog customDialog = new CustomDialog(mView, mComic.getTitle(), "确认删除选中的漫画章节？");
            customDialog.setListener(new CustomDialog.onClickListener() {
                @Override
                public void OnClickConfirm() {
                    deleteDBDownloadComic();
                    if (customDialog.isShowing()) {
                        customDialog.dismiss();
                    }
                }

                @Override
                public void OnClickCancel() {
                    if (customDialog.isShowing()) {
                        customDialog.dismiss();
                    }
                }
            });
            customDialog.show();
        } else {
            mView.showToast("请选择需要删除的章节数");
        }
    }

    public void deleteDBDownloadComic() {
        List<DBDownloadItem> deleteComics = new ArrayList<>();
        for (int i = 0; i < mLists.size(); i++) {
            if (selectMap.get(i) == Constants.CHAPTER_SELECTED) {
                deleteComics.add(mLists.get(i));
                if (downloadMap.containsKey(mLists.get(i).getChapters())) {
                    //如果删除的的正好在下载队列中，则移除
                    downloadMap.remove(mLists.get(i).getChapters());
                }
            }
        }
        DisposableSubscriber<List<DBDownloadItem>> disposableSubscriber = mDataSource.deleteDBDownloadComic(deleteComics, mComic)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSubscriber<List<DBDownloadItem>>() {
                    @Override
                    public void onNext(List<DBDownloadItem> items) {
                        downloadedNum = 0;
                        mLists.clear();
                        if (items != null && items.size() != 0) {
                            //刷新列表
                            mLists.addAll(items);
                            mView.fillData(mLists);
                            //初始化选择
                            clearSelect();
                            //判断有多少是之前已经下载过的
                            for (int i = 0; i < items.size(); i++) {
                                if (items.get(i).getState() == DownState.FINISH) {
                                    downloadedNum++;
                                }
                            }
                            //判断是否全部下载完了
                            if (downloadedNum == mLists.size()) {
                                mView.onDownloadFinished();
                                mComic.setState(DownState.FINISH);
                            } else {
                                mComic.setState(DownState.DOWN);
                            }
                            mComic.setDownload_num_finish(downloadedNum);
                            mComic.setDownload_num(items.size());
                        } else {
                            mComic.setStateInte(-1);
                            mView.finish();
                        }
                    }

                    @Override
                    public void onError(Throwable t) {

                    }

                    @Override
                    public void onComplete() {
                        mView.quitEdit();
                    }
                });
        mCompositeDisposable.add(disposableSubscriber);
    }
}