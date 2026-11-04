#ifndef FALLOUT_STYLUS_H_
#define FALLOUT_STYLUS_H_

#include <cstdint>
#include <cstdlib>
#include <deque>

namespace fallout {

// Matches SDLSurface.STYLUS_TOUCH_DEVICE_ID. SDL reserves only -1 for mouse touch.
constexpr std::int64_t kStylusTouchDeviceId = -1398036812;

struct StylusEvent {
    int x;
    int y;
    int buttons; // Same left/right bit values as MOUSE_STATE_*.
};

// Independent of SDL so ordering, cancellation and timing can be regression tested.
class StylusInput {
public:
    void begin(int x, int y, std::uint32_t time)
    {
        if (active_) end(x_, y_, time, true);
        active_ = true;
        dragging_ = false;
        longPressed_ = false;
        startX_ = x_ = x;
        startY_ = y_ = y;
        started_ = time;
        emit(x, y, 0, true);
    }

    void move(int x, int y)
    {
        if (!active_) return;
        x_ = x;
        y_ = y;
        if (!dragging_ && !longPressed_
            && (std::abs(x - startX_) >= 8 || std::abs(y - startY_) >= 8)) {
            dragging_ = true;
            // Press on the original object before moving it.
            emit(startX_, startY_, 1);
        }
        emit(x, y, dragging_ ? 1 : 0);
    }

    void update(std::uint32_t time)
    {
        if (active_ && !dragging_ && !longPressed_ && time - started_ >= 500) {
            longPressed_ = true;
            emit(x_, y_, 2);
            emit(x_, y_, 0);
        }
    }

    void end(int x, int y, std::uint32_t time, bool cancelled = false)
    {
        if (!active_) return;
        if (!cancelled) {
            move(x, y);
            update(time); // A long stroke may begin/end between game frames.
            if (!dragging_ && !longPressed_) emit(x, y, 1);
        }
        emit(x, y, 0, true);
        active_ = false;
    }

    bool poll(StylusEvent& event)
    {
        if (!events_.empty()) {
            event = events_.front().event;
            events_.pop_front();
            return true;
        }
        if (active_) {
            event = { x_, y_, dragging_ ? 1 : 0 };
            return true;
        }
        return false;
    }

private:
    struct Pending {
        StylusEvent event;
        bool transition;
    };

    void emit(int x, int y, int buttons, bool boundary = false)
    {
        bool transition = boundary || buttons != lastButtons_;
        // High-rate pen motion must not build up a multi-frame cursor lag. Never
        // collapse button edges: even down/up in one SDL batch must both survive.
        if (!transition && !events_.empty() && !events_.back().transition) {
            events_.back().event = { x, y, buttons };
        } else {
            events_.push_back({ { x, y, buttons }, transition });
        }
        lastButtons_ = buttons;
    }

    std::deque<Pending> events_;
    bool active_ = false;
    bool dragging_ = false;
    bool longPressed_ = false;
    int startX_ = 0;
    int startY_ = 0;
    int x_ = 0;
    int y_ = 0;
    int lastButtons_ = 0;
    std::uint32_t started_ = 0;
};

} // namespace fallout

#endif
