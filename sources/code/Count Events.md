```
****public**** ****class**** Solution {

****private**** ****static**** ****final**** ****long**** timeWindow = 5 * 60 * 1000; __// ms__

__// 102, 100, 103, 110, ..................,__

__// BBT -> TreeSet<Long>__

****private**** ****final**** Deque<Long> events;

****public**** Solution() {

events = ****new**** LinkedList<>();

}

  

__// assume timestamp in ms__

****public**** ****void**** addEvent(****long**** timestamp) {

events.addLast(timestamp);

}

__// time window = 10__

__// now = 112__

****public**** ****int**** countRecentEvents(****long**** now) {

****long**** oldestMessageTimestamp = now - timeWindow;

****while**** (events.size() > 0 && events.peekFirst() < oldestMessageTimestamp) {

events.removeFirst();

}

****return**** events.size();

}

}
```
