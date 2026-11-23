#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <vector>
#include <string>

static void setWindowSizeInternal(int masterFd, int rows, int cols) {
    if (masterFd < 0) return;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    ws.ws_col = static_cast<unsigned short>(cols > 0 ? cols : 80);
    ioctl(masterFd, TIOCSWINSZ, &ws);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_yin_term_data_datasource_NativePty_createSubprocess(
    JNIEnv *env,
    jobject thiz,
    jstring cmd,
    jstring cwd,
    jobjectArray args,
    jobjectArray envVars,
    jint rows,
    jint cols
) {
    (void)thiz;
    if (cmd == nullptr) return nullptr;

    int masterFd = posix_openpt(O_RDWR | O_NOCTTY);
    if (masterFd < 0) {
        return nullptr;
    }

    if (grantpt(masterFd) != 0 || unlockpt(masterFd) != 0) {
        close(masterFd);
        return nullptr;
    }

    char slaveName[128];
    if (ptsname_r(masterFd, slaveName, sizeof(slaveName)) != 0) {
        close(masterFd);
        return nullptr;
    }

    fcntl(masterFd, F_SETFD, FD_CLOEXEC);
    setWindowSizeInternal(masterFd, rows, cols);

    struct termios tios;
    if (tcgetattr(masterFd, &tios) == 0) {
#ifdef IUTF8
        tios.c_iflag |= IUTF8;
#endif
        // Disable kernel line echo because Compose input bar renders user command input
        tios.c_lflag &= ~ECHO;
        // Keep ISIG enabled so byte 0x03 (Ctrl+C) sends SIGINT to foreground process group
        tios.c_lflag |= ISIG;
        tcsetattr(masterFd, TCSANOW, &tios);
    }

    const char *cmdUtf = env->GetStringUTFChars(cmd, nullptr);
    std::string cmdStr(cmdUtf ? cmdUtf : "/system/bin/sh");
    if (cmdUtf) env->ReleaseStringUTFChars(cmd, cmdUtf);

    std::string cwdStr("/");
    if (cwd != nullptr) {
        const char *cwdUtf = env->GetStringUTFChars(cwd, nullptr);
        if (cwdUtf) {
            cwdStr = cwdUtf;
            env->ReleaseStringUTFChars(cwd, cwdUtf);
        }
    }

    std::vector<std::string> argStrings;
    if (args != nullptr) {
        jsize argCount = env->GetArrayLength(args);
        argStrings.reserve(static_cast<size_t>(argCount));
        for (jsize i = 0; i < argCount; ++i) {
            auto jArg = static_cast<jstring>(env->GetObjectArrayElement(args, i));
            if (jArg != nullptr) {
                const char *utf = env->GetStringUTFChars(jArg, nullptr);
                if (utf) {
                    argStrings.emplace_back(utf);
                    env->ReleaseStringUTFChars(jArg, utf);
                }
                env->DeleteLocalRef(jArg);
            }
        }
    }
    if (argStrings.empty()) {
        argStrings.push_back(cmdStr);
    }

    std::vector<std::string> envStrings;
    if (envVars != nullptr) {
        jsize envCount = env->GetArrayLength(envVars);
        envStrings.reserve(static_cast<size_t>(envCount));
        for (jsize i = 0; i < envCount; ++i) {
            auto jEnv = static_cast<jstring>(env->GetObjectArrayElement(envVars, i));
            if (jEnv != nullptr) {
                const char *utf = env->GetStringUTFChars(jEnv, nullptr);
                if (utf) {
                    envStrings.emplace_back(utf);
                    env->ReleaseStringUTFChars(jEnv, utf);
                }
                env->DeleteLocalRef(jEnv);
            }
        }
    }

    std::vector<char *> argvPtrs;
    argvPtrs.reserve(argStrings.size() + 1);
    for (auto &s : argStrings) {
        argvPtrs.push_back(const_cast<char *>(s.c_str()));
    }
    argvPtrs.push_back(nullptr);

    pid_t pid = fork();
    if (pid < 0) {
        close(masterFd);
        return nullptr;
    }

    if (pid == 0) {
        // Child process: reset signals and attach slave PTY as controlling terminal
        signal(SIGINT, SIG_DFL);
        signal(SIGQUIT, SIG_DFL);
        signal(SIGTSTP, SIG_DFL);
        signal(SIGTTIN, SIG_DFL);
        signal(SIGTTOU, SIG_DFL);
        signal(SIGCHLD, SIG_DFL);
        signal(SIGPIPE, SIG_DFL);

        setsid();

        int slaveFd = open(slaveName, O_RDWR);
        if (slaveFd < 0) {
            _exit(127);
        }

#ifdef TIOCSCTTY
        ioctl(slaveFd, TIOCSCTTY, 0);
#endif

        dup2(slaveFd, STDIN_FILENO);
        dup2(slaveFd, STDOUT_FILENO);
        dup2(slaveFd, STDERR_FILENO);
        if (slaveFd > STDERR_FILENO) {
            close(slaveFd);
        }

        if (!envStrings.empty()) {
            clearenv();
            for (const auto &kv : envStrings) {
                size_t eqPos = kv.find('=');
                if (eqPos != std::string::npos && eqPos > 0) {
                    std::string key = kv.substr(0, eqPos);
                    std::string val = kv.substr(eqPos + 1);
                    setenv(key.c_str(), val.c_str(), 1);
                }
            }
        }

        if (!cwdStr.empty()) {
            if (chdir(cwdStr.c_str()) != 0) {
                chdir("/");
            }
        }

        execvp(cmdStr.c_str(), argvPtrs.data());
        _exit(127);
    }

    jintArray result = env->NewIntArray(2);
    if (result == nullptr) {
        close(masterFd);
        kill(pid, SIGKILL);
        waitpid(pid, nullptr, 0);
        return nullptr;
    }

    jint pair[2] = { static_cast<jint>(masterFd), static_cast<jint>(pid) };
    env->SetIntArrayRegion(result, 0, 2, pair);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yin_term_data_datasource_NativePty_setWindowSize(
    JNIEnv *env,
    jobject thiz,
    jint masterFd,
    jint rows,
    jint cols
) {
    (void)env;
    (void)thiz;
    setWindowSizeInternal(masterFd, rows, cols);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yin_term_data_datasource_NativePty_readFromPty(
    JNIEnv *env,
    jobject thiz,
    jint masterFd,
    jbyteArray buffer
) {
    (void)thiz;
    if (masterFd < 0 || buffer == nullptr) return -1;

    jsize cap = env->GetArrayLength(buffer);
    if (cap <= 0) return 0;

    jbyte *nativeBuf = env->GetByteArrayElements(buffer, nullptr);
    if (nativeBuf == nullptr) return -1;

    ssize_t bytesRead;
    do {
        bytesRead = read(masterFd, nativeBuf, static_cast<size_t>(cap));
    } while (bytesRead < 0 && errno == EINTR);

    env->ReleaseByteArrayElements(buffer, nativeBuf, 0);
    return bytesRead > 0 ? static_cast<jint>(bytesRead) : -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yin_term_data_datasource_NativePty_writeToPty(
    JNIEnv *env,
    jobject thiz,
    jint masterFd,
    jbyteArray data,
    jint length
) {
    (void)thiz;
    if (masterFd < 0 || data == nullptr || length <= 0) return -1;

    jsize cap = env->GetArrayLength(data);
    jint toWrite = length < cap ? length : cap;

    jbyte *nativeBuf = env->GetByteArrayElements(data, nullptr);
    if (nativeBuf == nullptr) return -1;

    jint totalWritten = 0;
    while (totalWritten < toWrite) {
        ssize_t written = write(
            masterFd,
            nativeBuf + totalWritten,
            static_cast<size_t>(toWrite - totalWritten)
        );
        if (written < 0) {
            if (errno == EINTR) continue;
            env->ReleaseByteArrayElements(data, nativeBuf, JNI_ABORT);
            return -1;
        }
        totalWritten += static_cast<jint>(written);
    }

    env->ReleaseByteArrayElements(data, nativeBuf, JNI_ABORT);
    return totalWritten;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yin_term_data_datasource_NativePty_sendSignal(
    JNIEnv *env,
    jobject thiz,
    jint pid,
    jint signalNumber
) {
    (void)env;
    (void)thiz;
    if (pid <= 0) return;
    // Negative PID targets the entire foreground process group
    kill(-pid, signalNumber);
    kill(pid, signalNumber);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_yin_term_data_datasource_NativePty_waitForProcess(
    JNIEnv *env,
    jobject thiz,
    jint pid
) {
    (void)env;
    (void)thiz;
    if (pid <= 0) return -1;
    int status = 0;
    pid_t res;
    do {
        res = waitpid(pid, &status, 0);
    } while (res < 0 && errno == EINTR);

    if (res < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yin_term_data_datasource_NativePty_closePty(
    JNIEnv *env,
    jobject thiz,
    jint masterFd
) {
    (void)env;
    (void)thiz;
    if (masterFd >= 0) {
        close(masterFd);
    }
}