#include "../src/stylus.h"
#include <cassert>
#include <algorithm>
#include <iostream>
#include <vector>

using namespace fallout;

static std::vector<StylusEvent> drain(StylusInput& pen)
{
    std::vector<StylusEvent> events;
    StylusEvent event;
    while (pen.poll(event)) {
        events.push_back(event);
        assert(events.size() < 100); // Call only after a completed stroke.
    }
    return events;
}

static int presses(const std::vector<StylusEvent>& events, int button)
{
    int count = 0;
    int previous = 0;
    for (const auto& event : events) {
        if ((event.buttons & button) && !(previous & button)) ++count;
        previous = event.buttons;
    }
    return count;
}

int main()
{
    { // An entire tap in one input batch still has ordered position/down/up.
        StylusInput pen;
        pen.begin(300, 120, 1000);
        pen.end(300, 120, 1050);
        auto events = drain(pen);
        assert(events.front().x == 300 && events.front().y == 120);
        assert(events.front().buttons == 0 && events.back().buttons == 0);
        assert(presses(events, 1) == 1 && presses(events, 2) == 0);
    }
    { // Quick consecutive taps must not merge into a single held button.
        StylusInput pen;
        pen.begin(10, 20, 100);
        pen.end(10, 20, 150);
        pen.begin(50, 60, 180);
        pen.end(50, 60, 220);
        assert(presses(drain(pen), 1) == 2);
    }
    { // Long press emits one right click, with no initial/final left click.
        StylusInput pen;
        pen.begin(40, 50, 100);
        pen.update(600);
        pen.update(2000);
        pen.end(40, 50, 2200);
        auto events = drain(pen);
        assert(presses(events, 1) == 0 && presses(events, 2) == 1);
        assert(events.back().buttons == 0);
    }
    { // A stalled frame does not turn a long press into a left tap.
        StylusInput pen;
        pen.begin(40, 50, 100);
        pen.end(40, 50, 800);
        auto events = drain(pen);
        assert(presses(events, 1) == 0 && presses(events, 2) == 1);
    }
    { // Drag selects at the initial location, then holds and releases left.
        StylusInput pen;
        pen.begin(10, 20, 100);
        for (int i = 1; i < 500; ++i) pen.move(10 + i, 20);
        pen.end(509, 20, 900);
        auto events = drain(pen);
        assert(events.size() < 10); // Motion coalesces; edges survive.
        assert(presses(events, 1) == 1 && presses(events, 2) == 0);
        auto down = std::find_if(events.begin(), events.end(), [](const StylusEvent& event) { return event.buttons == 1; });
        assert(down != events.end() && down->x == 10 && down->y == 20);
        assert(events.back().x == 509 && events.back().buttons == 0);
    }
    { // Cancellation is never a click.
        StylusInput pen;
        pen.begin(40, 50, 100);
        pen.end(40, 50, 800, true);
        auto events = drain(pen);
        assert(presses(events, 1) == 0 && presses(events, 2) == 0);
    }
    { // Tick rollover preserves the long-press threshold.
        StylusInput pen;
        pen.begin(40, 50, UINT32_MAX - 100);
        pen.end(40, 50, 450);
        assert(presses(drain(pen), 2) == 1);
    }
    { // A drag remains held even when there are no fresh movement events.
        StylusInput pen;
        pen.begin(0, 0, 100);
        pen.move(20, 20);
        StylusEvent event;
        for (int i = 0; i < 10; ++i) assert(pen.poll(event));
        assert(event.buttons == 1 && event.x == 20 && event.y == 20);
        pen.end(20, 20, 200, true);
        assert(drain(pen).back().buttons == 0);
    }
    std::cout << "8 stylus regression scenarios passed\n";
}
