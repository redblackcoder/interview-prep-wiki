package com.practice.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedQueue {

    // a circular buffer to represent a queue
    private int[] queue;
    private int size;

    // head and tail pointers
    // head == tail => queue is empty
    // head == (tail + 1) % size => queue is full
    private int head;
    private int tail;

    private final Lock qLock;
    private final Condition qFull;
    private final Condition qEmpty;

    public BoundedQueue(int cap) {
        queue = new int[cap + 1];
        size = cap + 1;
        head = 0;
        tail = 0;
        qLock = new ReentrantLock();
        qFull = qLock.newCondition();
        qEmpty = qLock.newCondition();
    }

    public boolean enqueue(int val) {
        qLock.lock();
        try {
            while (Math.floorMod(tail + 1, size) == head) {
                if (Thread.currentThread().isInterrupted()) {
                    return false;
                }

                try {
                    qFull.await();
                } catch (InterruptedException inte) {
                    Thread.currentThread().interrupt();
                }
            }

            queue[tail] = val;
            tail = Math.floorMod(tail + 1, size);
            qEmpty.signal();
            return true;
        } finally {
            qLock.unlock();
        }
    }

    // assuming positive values only, so -1 implies queue is empty
    public int dequeue() {
        qLock.lock();
        try {
            while (head == tail) {
                if (Thread.currentThread().isInterrupted()) {
                    return -1;
                }

                try {
                    qEmpty.await();
                } catch (InterruptedException inte) {
                    Thread.currentThread().interrupt();
                }
            }

            int val = queue[head];
            head = Math.floorMod(head + 1, size);
            qFull.signal();
            return val;
        } finally {
            qLock.unlock();
        }
    }
}
