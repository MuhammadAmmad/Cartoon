package com.tplink.cartoon.ui.view;
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

public interface IChapterView<T> extends ILoadDataView<T> {
    //弹出菜单
    void showMenu();

    //下一章
    void nextChapter(T data, int loadingPosition, int offset);

    //前一章
    void preChapter(T data, int loadingPosition, int offset);

    //切换预览模式
    void switchModel(int a);

    //前一页
    void prePage();

    //下一页
    void nextPage();

    void setTitle(String name);
}
