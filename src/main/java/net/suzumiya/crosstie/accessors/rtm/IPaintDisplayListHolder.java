package net.suzumiya.crosstie.accessors.rtm;

import jp.ngt.ngtlib.renderer.DisplayList;

/**
 * {@code TileEntityPaint} にクライアント側 DisplayList キャッシュと dirty フラグを保持させるアクセサ。
 */
public interface IPaintDisplayListHolder {
    DisplayList crosstie$getPaintGLList();

    void crosstie$setPaintGLList(DisplayList list);

    boolean crosstie$isPaintDirty();

    void crosstie$setPaintDirty(boolean dirty);

    boolean crosstie$isPaintEmpty();

    void crosstie$setPaintEmpty(boolean empty);
}
