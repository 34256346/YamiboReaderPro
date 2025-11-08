package org.shirakawatyu.yamibo.novel.bean;

public class ReaderSettings {
    private Float fontSizePx;
    private Float lineHeightPx;
    private Float paddingDp;// 改为了Float

    private Boolean nightMode;
    private Boolean loadImages;
    private String backgroundColor;
    private Boolean isVerticalMode;

    public ReaderSettings(Float fontSizePx, Float lineHeightPx, Float padding, Boolean nightMode, String backgroundColor, Boolean loadImages, Boolean isVerticalMode) {
        this.fontSizePx = fontSizePx;
        this.lineHeightPx = lineHeightPx;
        this.paddingDp = padding;
        this.nightMode = nightMode;
        this.backgroundColor = backgroundColor;
        this.loadImages = loadImages;
        this.isVerticalMode = isVerticalMode;

    }

    public ReaderSettings() {
    }

    public Float getFontSizePx() {
        return fontSizePx;
    }

    public void setFontSizePx(Float fontSizePx) {
        this.fontSizePx = fontSizePx;
    }

    public Float getLineHeightPx() {
        return lineHeightPx;
    }

    public void setLineHeightPx(Float lineHeightPx) {
        this.lineHeightPx = lineHeightPx;
    }

    public Float getPaddingDp() {
        return paddingDp;
    }

    public void setPaddingDp(Float paddingDp) {
        this.paddingDp = paddingDp;
    }

    public Boolean getNightMode() {
        return nightMode;
    }

    public void setNightMode(Boolean nightMode) {
        this.nightMode = nightMode;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public Boolean getLoadImages() {
        return loadImages;
    }

    public void setLoadImages(Boolean loadImages) {
        this.loadImages = loadImages;
    }

    public Boolean getIsVerticalMode() {
        return isVerticalMode;
    }

    public void setIsVerticalMode(Boolean isVerticalMode) {
        this.isVerticalMode = isVerticalMode;
    }
}
