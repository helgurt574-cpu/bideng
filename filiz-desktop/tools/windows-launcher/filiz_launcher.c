#define UNICODE
#define _UNICODE
#include <windows.h>
#include <wchar.h>

static void dirname_in_place(wchar_t *path) {
    size_t len = wcslen(path);
    while (len > 0 && path[len - 1] != L'\\' && path[len - 1] != L'/') {
        path[--len] = L'\0';
    }
    if (len > 0) {
        path[len - 1] = L'\0';
    }
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR commandLine, int showCommand) {
    (void) instance;
    (void) previous;
    (void) showCommand;

    wchar_t baseDir[MAX_PATH];
    if (GetModuleFileNameW(NULL, baseDir, MAX_PATH) == 0) {
        MessageBoxW(NULL, L"Bideng.exe konumu okunamadi.", L"Bideng", MB_ICONERROR);
        return 1;
    }
    dirname_in_place(baseDir);

    wchar_t javaExe[MAX_PATH];
    swprintf(javaExe, MAX_PATH, L"%ls\\runtime\\bin\\javaw.exe", baseDir);

    DWORD attributes = GetFileAttributesW(javaExe);
    if (attributes == INVALID_FILE_ATTRIBUTES || (attributes & FILE_ATTRIBUTE_DIRECTORY)) {
        MessageBoxW(NULL, L"Gomulu Java runtime bulunamadi: runtime\\bin\\javaw.exe", L"Bideng", MB_ICONERROR);
        return 1;
    }

    wchar_t fullCommand[32768];
    swprintf(
        fullCommand,
        32768,
        L"\"%ls\" -Dbideng.app.dir=\"%ls\" -cp \"%ls\\app\\lib\\*\" com.example.filiz.desktop.MainKt %ls",
        javaExe,
        baseDir,
        baseDir,
        commandLine ? commandLine : L""
    );

    STARTUPINFOW startupInfo;
    PROCESS_INFORMATION processInfo;
    ZeroMemory(&startupInfo, sizeof(startupInfo));
    ZeroMemory(&processInfo, sizeof(processInfo));
    startupInfo.cb = sizeof(startupInfo);

    BOOL started = CreateProcessW(
        NULL,
        fullCommand,
        NULL,
        NULL,
        FALSE,
        0,
        NULL,
        baseDir,
        &startupInfo,
        &processInfo
    );

    if (!started) {
        MessageBoxW(NULL, L"Bideng baslatilamadi.", L"Bideng", MB_ICONERROR);
        return 1;
    }

    CloseHandle(processInfo.hThread);
    CloseHandle(processInfo.hProcess);
    return 0;
}
