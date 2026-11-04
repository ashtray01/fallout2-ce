#ifndef FALLOUT_STYLUS_H_
#define FALLOUT_STYLUS_H_

#include <cstdint>
#include <deque>

namespace fallout {

// Fallout uses three synthetic SDL touch channels for the Android pen. The
// values must match the device IDs in SDLSurface.java. SDL reserves only -1
// for its own synthetic mouse touch.
constexpr std::int64_t kStylusTouchDeviceId = -1398036812;
constexpr std::int64_t kStylusHoverTouchDeviceId = -1398036811;
constexpr std::int64_t kStylusButtonTouchDeviceId = -1398036810;

struct StylusEvent {
    int x;
    int y;
    int buttons; // Same left/right bit values as MOUSE_STATE_*.
};

// Translates absolute pen samples into the same mouse state stream the engine
// receives from a real mouse. When the pen touches the screen the left button
// is held down, so the engine's own repeat timer opens the original action
// menu after `BUTTON_REPEAT_TIME`; no Android-side menu is involved.
//
// Independent of SDL so ordering, cancellation and coalescing can be
// regression tested.
class StylusInput {
public:
    void begin(int x, int y)
    {
        // A lost pen-up must not leave the left button stuck.
        if (touching_)
            end(x_, y_, true);
        touching_ = true;
        x_ = x;
        y_ = y;
        // Contact is an immediate left press. Reporting it only on release
        // would make holding the pen impossible to express to the engine.
        emit(x, y, kLeftButton, true);
    }

    void move(int x, int y)
    {
        if (!touching_) {
            hover(x, y);
            return;
        }
        x_ = x;
        y_ = y;
        emit(x, y, kLeftButton);
    }

    // Moves the game cursor without pressing a button (pen in range).
    void hover(int x, int y)
    {
        if (touching_)
            return;
        x_ = x;
        y_ = y;
        emit(x, y, 0);
    }

    void end(int x, int y, bool cancelled = false)
    {
        if (!touching_)
            return;
        if (!cancelled) {
            x_ = x;
            y_ = y;
            // Keep the last position of a drag, then release.
            emit(x, y, kLeftButton);
        }
        emit(x, y, 0, true);
        touching_ = false;
    }

    // Releases the pen without a click (focus loss, gesture cancellation).
    void cancel()
    {
        end(x_, y_, true);
    }

    // One physical side-button edge produces one complete right click. Contact
    // has priority: an edge during a stroke is ignored so that the in-progress
    // left click or drag cannot turn into an unrelated action.
    void sideButton(int x, int y)
    {
        if (touching_)
            return;
        x_ = x;
        y_ = y;
        emit(x, y, 0);
        emit(x, y, kRightButton, true);
        emit(x, y, 0, true);
    }

    bool touching() const
    {
        return touching_;
    }

    // Returns the next mouse state. While the pen touches the screen the held
    // left button is reported on every frame, which is what makes the engine
    // repeat timer work.
    bool poll(StylusEvent& event)
    {
        if (!events_.empty()) {
            event = events_.front().event;
            events_.pop_front();
            return true;
        }
        if (touching_) {
            event = { x_, y_, kLeftButton };
            return true;
        }
        return false;
    }

private:
    enum : int {
        kLeftButton = 0x01,
        kRightButton = 0x02,
    };

    struct Pending {
        StylusEvent event;
        bool transition;
    };

    void emit(int x, int y, int buttons, bool boundary = false)
    {
        bool transition = boundary || buttons != lastButtons_;
        // High-rate pen motion must not build up multi-frame cursor lag. Never
        // collapse button edges: even a full click delivered in one SDL batch
        // must keep both down and up.
        if (!transition && !events_.empty() && !events_.back().transition) {
            events_.back().event = { x, y, buttons };
        } else {
            events_.push_back({ { x, y, buttons }, transition });
        }
        lastButtons_ = buttons;
    }

    std::deque<Pending> events_;
    bool touching_ = false;
    int x_ = 0;
    int y_ = 0;
    int lastButtons_ = 0;
};

} // namespace fallout

#endif
