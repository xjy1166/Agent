package com.baidu.fsg.uid.buffer;

/**

 * @description: 处理器
 
 **/

@FunctionalInterface
public interface RejectedTakeBufferHandler {

    void rejectTakeBuffer(RingBuffer ringBuffer);
}
