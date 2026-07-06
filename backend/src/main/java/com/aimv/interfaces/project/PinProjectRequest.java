package com.aimv.interfaces.project;

/** 置顶/取消置顶请求体：pinned=true 置顶，false 取消。 */
public record PinProjectRequest(boolean pinned) {
}
