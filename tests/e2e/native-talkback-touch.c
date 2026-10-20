#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

static int fd;
static void event(unsigned short type, unsigned short code, int value) {
    struct input_event e = {0};
    e.type = type; e.code = code; e.value = value;
    if (write(fd, &e, sizeof(e)) != sizeof(e)) { perror("native input write"); exit(2); }
}
static void sync_input(void) { event(EV_SYN, SYN_REPORT, 0); }
static void position(int x, int y) {
    event(EV_ABS, ABS_MT_POSITION_X, x); event(EV_ABS, ABS_MT_POSITION_Y, y);
}
static void down(int id, int x, int y) {
    event(EV_ABS, ABS_MT_SLOT, 0); event(EV_ABS, ABS_MT_TRACKING_ID, id);
    event(EV_ABS, ABS_MT_TOOL_TYPE, MT_TOOL_FINGER);
    event(EV_ABS, ABS_MT_PRESSURE, 512); event(EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
    event(EV_ABS, ABS_MT_WIDTH_MAJOR, 0); event(EV_ABS, ABS_Z, 1);
    position(x, y); sync_input();
}
static void up(void) {
    event(EV_ABS, ABS_Z, 0); event(EV_ABS, ABS_MT_TRACKING_ID, -1); sync_input();
}
static int coordinate(const char *s) {
    char *end; long value = strtol(s, &end, 10);
    if (*s == '\0' || *end != '\0' || value < 0 || value > 32767) exit(2);
    return (int)value;
}
int main(int argc, char **argv) {
    if (argc != 6 || strncmp(argv[1], "/dev/input/event", 16) != 0) return 2;
    int start = coordinate(argv[3]), end = coordinate(argv[4]), y = coordinate(argv[5]);
    if (strcmp(argv[2], "swipe") != 0 && strcmp(argv[2], "doubletap") != 0 && strcmp(argv[2], "release") != 0) return 2;
    fd = open(argv[1], O_WRONLY | O_CLOEXEC);
    if (fd < 0) { perror("native input open"); return 2; }
    struct timespec now; clock_gettime(CLOCK_MONOTONIC, &now);
    int id = (int)(now.tv_nsec % 60000) + 1;
    if (strcmp(argv[2], "swipe") == 0) {
        down(id, start, y);
        for (int i = 1; i <= 8; ++i) { usleep(10000); position(start + (end-start)*i/8, y); sync_input(); }
        up();
    } else if (strcmp(argv[2], "release") == 0) {
        up();
    } else {
        for (int i = 0; i < 2; ++i) { down(id+i, start, y); usleep(40000); up(); usleep(70000); }
    }
    close(fd);
    return 0;
}
