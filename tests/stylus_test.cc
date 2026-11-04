#include "../src/stylus.h"
#include <algorithm>
#include <cassert>
#include <iostream>
#include <vector>

using namespace fallout;

static std::vector<StylusEvent> drain(StylusInput& pen, int frames = 100)
{
    std::vector<StylusEvent> events;
    StylusEvent event;
    while (pen.poll(event)) {
        events.push_back(event);
        assert(events.size() < 1000);
        if (pen.touching() && (int)events.size() >= frames) break;
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

static bool onlyLeftButton(const std::vector<StylusEvent>& events)
{
    for (const auto& event : events) {
        if (event.buttons & 0x02) return false;
    }
    return true;
}

int main()
{
    { // Test 5: a tap is an ordered position/down/up sequence.
        StylusInput pen;
        pen.begin(300, 120);
        pen.end(300, 120);
        auto events = drain(pen);
        assert(events.front().x == 300 && events.front().y == 120);
        assert(events.front().buttons == 1);
        assert(events.back().buttons == 0);
        assert(presses(events, 1) == 1 && presses(events, 2) == 0);
    }
    { // Test 6: contact presses the left button immediately, not on release.
        StylusInput pen;
        pen.begin(40, 50);
        StylusEvent event;
        assert(pen.poll(event));
        assert(event.x == 40 && event.y == 50 && event.buttons == 1);
        assert(pen.touching());
    }
    { // Test 6: a long hold keeps reporting the left button; the engine's own
      // repeat timer (BUTTON_REPEAT_TIME) then opens the action menu.
        StylusInput pen;
        pen.begin(40, 50);
        auto events = drain(pen, 180);
        assert((int)events.size() == 180);
        for (const auto& event : events) {
            assert(event.buttons == 1);
        }
        pen.end(40, 50);
        auto tail = drain(pen);
        events.insert(events.end(), tail.begin(), tail.end());
        assert(events.back().buttons == 0);
        assert(presses(events, 1) == 1); // Exactly one press for the whole hold.
        assert(onlyLeftButton(events));
    }
    { // Test 6/7: the menu keeps receiving absolute movement while held.
        StylusInput pen;
        pen.begin(100, 100);
        pen.end(100, 100); // Entire tap fits into one frame at the start.
        drain(pen);
        assert(!pen.touching());

        pen.begin(100, 100);
        drain(pen, 20);
        for (int i = 1; i <= 20; ++i) {
            pen.move(100, 100 + i * 12); // Menu rows are 10 px apart.
        }
        auto moved = drain(pen);
        assert(!moved.empty());
        assert(moved.back().buttons == 1);
        assert(moved.back().y == 340);
        pen.end(100, 340);
        auto events = drain(pen);
        assert(events.back().buttons == 0);
        assert(onlyLeftButton(events));
    }
    { // Test 1/2/3: one side-button edge is one right click, no repeats.
        StylusInput pen;
        pen.hover(10, 20);
        drain(pen);
        pen.sideButton(10, 20);
        auto events = drain(pen);
        assert(presses(events, 2) == 1 && presses(events, 1) == 0);
        assert(events.back().buttons == 0);

        // Holding the physical button produces no further events: contact is
        // not active and no press is pending.
        StylusEvent ignored;
        assert(!pen.poll(ignored));

        // Test 3: the next press after release works again.
        pen.sideButton(30, 40);
        events = drain(pen);
        assert(presses(events, 2) == 1);
        assert(events.back().x == 30 && events.back().y == 40);
    }
    { // Side button during a stroke: the left stroke is discarded and replaced
      // by exactly one right click. Releasing the pen adds no left-up.
        StylusInput pen;
        pen.begin(10, 10);
        pen.sideButton(20, 20);
        auto events = drain(pen);
        assert(presses(events, 2) == 1);
        assert(events.back().buttons == 0);
        pen.end(30, 30);
        StylusEvent none;
        assert(!pen.poll(none));
    }
    { // Test 4: hover moves the absolute pointer without any button.
        StylusInput pen;
        pen.hover(200, 150);
        auto events = drain(pen);
        assert(!events.empty());
        assert(events.back().x == 200 && events.back().y == 150);
        assert(events.back().buttons == 0);
        assert(presses(events, 1) == 0 && presses(events, 2) == 0);
        assert(!pen.touching());
    }
    { // Test 8: cancellation releases the button without a click.
        StylusInput pen;
        pen.begin(40, 50);
        drain(pen, 5);
        pen.cancel();
        auto events = drain(pen);
        assert(events.size() == 1);
        assert(events.back().buttons == 0);
        assert(presses(events, 1) == 0 && presses(events, 2) == 0);
    }
    { // A lost pen-up is recovered by the next contact.
        StylusInput pen;
        pen.begin(10, 10);
        drain(pen, 5);
        pen.begin(20, 20);
        auto events = drain(pen, 5);
        assert(events.front().buttons == 0); // Previous stroke released.
        assert(presses(events, 1) == 1); // New contact pressed once.
        assert(events.back().buttons == 1);
    }
    { // Drag: press at the origin, stay held, move, release at the end.
        StylusInput pen;
        pen.begin(10, 20);
        for (int i = 1; i < 500; ++i) pen.move(10 + i, 20);
        pen.end(509, 20);
        auto events = drain(pen);
        assert(events.size() < 10); // Motion coalesces; edges survive.
        assert(presses(events, 1) == 1 && presses(events, 2) == 0);
        auto down = std::find_if(events.begin(), events.end(), [](const StylusEvent& event) { return event.buttons == 1; });
        assert(down != events.end() && down->x == 10 && down->y == 20);
        assert(events.back().x == 509 && events.back().buttons == 0);
    }
    { // Two quick taps must not merge into a single held button.
        StylusInput pen;
        pen.begin(10, 20);
        pen.end(10, 20);
        pen.begin(50, 60);
        pen.end(50, 60);
        auto events = drain(pen);
        assert(presses(events, 1) == 2);
        assert(events.back().buttons == 0);
    }
    { // A side click between two taps is still one right click.
        StylusInput pen;
        pen.sideButton(5, 5);
        pen.begin(10, 20);
        pen.end(10, 20);
        pen.sideButton(7, 8);
        auto events = drain(pen);
        assert(presses(events, 2) == 2 && presses(events, 1) == 1);
    }
    std::cout << "12 stylus regression scenarios passed\n";
}

