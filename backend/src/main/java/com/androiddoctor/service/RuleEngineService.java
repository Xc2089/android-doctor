package com.androiddoctor.service;

import com.androiddoctor.dto.FindingDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@Service
public class RuleEngineService {
    private final List<DiagnosticRule> rules = List.of(
            new DiagnosticRule(
                    "NATIVE_LIB_NOT_FOUND", "Native 库加载失败", "Native/JNI", "high", 94,
                    log -> containsAny(log, "unsatisfiedlinkerror", "dlopen failed", "library not found", "couldn't find \"lib"),
                    List.of("APK/AAB 未打包目标 ABI 的 .so", "依赖库缺失或加载路径错误", "设备架构与 native 库不兼容"),
                    List.of("用 APK Analyzer 检查 lib/<abi>/ 下是否包含报错库", "确认所有依赖 .so 使用同一 STL 与 ABI", "打印 Build.SUPPORTED_ABIS，并核对 abiFilters / splits 配置")),
            new DiagnosticRule(
                    "ABI_MISMATCH", "32/64 位 ABI 不匹配", "ABI", "critical", 97,
                    log -> containsAny(log, "wrong elf class", "elfclass32", "elfclass64", "is 32-bit instead of 64-bit", "is 64-bit instead of 32-bit", "unexpected e_machine"),
                    List.of("64 位进程加载了 32 位库，或反之", "部分三方 SDK 未提供 arm64-v8a", "ABI split 安装不完整"),
                    List.of("同时提供 arm64-v8a 与 armeabi-v7a 的完整依赖闭包", "检查 android.defaultConfig.ndk.abiFilters", "在目标架构真机上检查 file/readelf 输出")),
            new DiagnosticRule(
                    "JNI_SIGNATURE", "JNI 方法或签名不匹配", "Native/JNI", "high", 93,
                    log -> containsAny(log, "no implementation found for", "jni detected error", "java_vm_ext.cc", "register natives", "jni signature", "failed to register native"),
                    List.of("Java/Kotlin native 声明与 C/C++ 函数签名不一致", "包名或类名混淆后未同步", "RegisterNatives 表中的名称/签名错误"),
                    List.of("使用 javap -s 或 Android Studio 生成并核对 JNI descriptor", "检查 extern \"C\"、JNIEXPORT/JNICALL 与参数顺序", "为 JNI 入口添加 keep 规则或改用显式 RegisterNatives")),
            new DiagnosticRule(
                    "PAGE_SIZE_16K", "16 KB 内存页兼容问题", "Native/16KB", "high", 91,
                    log -> containsAny(log, "16kb", "16 kb", "page size", "max-page-size", "segment alignment", "load segment alignment", "p_align"),
                    List.of("ELF LOAD 段仍按 4 KB 对齐", "旧版 NDK 或预编译三方 .so 不支持 16 KB 页", "打包过程改变了 native 库对齐"),
                    List.of("使用较新 NDK 重编译并启用 16 KB ELF 对齐", "执行 llvm-readelf -lW lib.so 检查 LOAD 段 Align", "升级或替换不兼容的预编译 SDK，并在 16 KB 模拟器/设备回归")),
            new DiagnosticRule(
                    "ANR", "应用无响应（ANR）", "ANR", "high", 96,
                    log -> containsAny(log, "anr in ", "application not responding", "input dispatching timed out", "broadcast of intent", "executing service"),
                    List.of("主线程执行阻塞 I/O、锁等待或重计算", "BroadcastReceiver/Service 超时", "Binder 调用或跨进程依赖卡死"),
                    List.of("先检查 main 线程堆栈顶部与锁持有者", "将磁盘、网络和重计算移出主线程", "结合 traces.txt、Perfetto 与 StrictMode 复现定位")),
            new DiagnosticRule(
                    "OUT_OF_MEMORY", "内存耗尽（OOM）", "Memory", "critical", 98,
                    log -> containsAny(log, "outofmemoryerror", "failed to allocate", "low memory killer", "oom_adj", "allocation failed"),
                    List.of("大图/大数组峰值过高", "对象、Context 或 native 内存泄漏", "进程内存上限或连续地址空间不足"),
                    List.of("采集 heap dump 并用 Memory Profiler/LeakCanary 检查支配树", "对图片降采样并避免一次性读入大文件", "同时检查 dumpsys meminfo 与 native malloc/图形内存")),
            new DiagnosticRule(
                    "SELINUX_DENIAL", "SELinux 权限拒绝", "Security/SELinux", "high", 95,
                    log -> containsAny(log, "avc: denied", "selinux", "permission denied") && containsAny(log, "scontext=", "tcontext=", "avc:"),
                    List.of("进程域无权访问目标文件或设备节点", "设备厂商 sepolicy 缺少规则", "目标对象标签不正确"),
                    List.of("记录完整 scontext、tcontext、tclass 与 permission", "系统应用在源码 sepolicy 中添加最小权限 allow 规则", "普通应用不要尝试绕过 SELinux，改用公开 API 或系统服务")),
            new DiagnosticRule(
                    "SERIAL_PORT", "串口打开或通信失败", "Hardware/Serial", "medium", 88,
                    log -> containsAny(log, "/dev/tty", "serialport", "serial port", "uart") && containsAny(log, "denied", "failed", "error", "timeout", "ioexception"),
                    List.of("设备节点权限或 SELinux 拒绝", "波特率/数据位/校验位配置不一致", "串口被其他进程占用或 USB 转串口断开"),
                    List.of("检查节点权限、所属组及 avc denied 日志", "核对两端 baud/parity/data/stop bits", "增加独占锁、断线重连、读写超时和帧校验")),
            new DiagnosticRule(
                    "HARMONY_SIGABRT", "HarmonyOS Native SIGABRT", "HarmonyOS/Native", "critical", 92,
                    log -> containsAny(log, "sigabrt", "signal 6") && containsAny(log, "ohos", "harmony", "hilog", "faultlogger", "musl", "libace", "ark"),
                    List.of("native 断言失败、double free 或主动 abort", "N-API/ArkTS 边界参数或生命周期错误", "系统/三方 native 库兼容性问题"),
                    List.of("保留 faultlogger 生成的崩溃文件并核对 BuildId", "用对应未剥离符号表进行 addr2line/llvm-symbolizer", "检查 abort message、崩溃线程与最近一次 N-API 调用")),
            new DiagnosticRule(
                    "NATIVE_CRASH", "Native 信号崩溃", "Native/Crash", "critical", 89,
                    log -> containsAny(log, "fatal signal 11", "sigsegv", "fatal signal 7", "sigbus", "backtrace:", "tombstone"),
                    List.of("空指针、越界访问、use-after-free", "未对齐访问或损坏的 native 堆", "JNI 跨线程/生命周期使用错误"),
                    List.of("按 BuildId 匹配符号表并符号化 backtrace", "启用 HWASan/ASan 或 GWP-ASan 复现", "从崩溃线程首个业务栈帧向上检查对象生命周期")),
            new DiagnosticRule(
                    "JAVA_CRASH", "Java/Kotlin 未捕获异常", "Java/Kotlin", "high", 86,
                    log -> containsAny(log, "fatal exception:", "uncaught exception", "androidruntime: fatal"),
                    List.of("业务异常未在边界处理", "空值、状态或线程模型假设不成立", "系统/三方 API 调用前置条件未满足"),
                    List.of("定位 Caused by 链中最底层业务异常", "优先修复第一个属于自身包名的堆栈帧", "补充可复现输入的单元测试或集成测试"))
    );

    public List<FindingDto> analyze(String log) {
        String normalized = log.toLowerCase(Locale.ROOT);
        List<FindingDto> findings = new ArrayList<>();
        for (DiagnosticRule rule : rules) {
            if (rule.matcher.test(normalized)) {
                findings.add(new FindingDto(rule.code, rule.title, rule.category, rule.severity,
                        rule.confidence, evidence(log, rule), rule.causes, rule.suggestions));
            }
        }
        if (findings.isEmpty()) {
            findings.add(new FindingDto(
                    "UNKNOWN", "暂未命中已知规则", "Unknown", "info", 30,
                    firstNonBlankLines(log, 3),
                    List.of("日志片段可能不完整", "问题类型尚未被内置规则覆盖"),
                    List.of("补充异常前后各 100 行、设备/系统版本与复现步骤", "上传完整 tombstone、ANR traces 或 hilog 文件", "可配置 OpenAI-compatible 服务做补充分析")));
        }
        findings.sort((a, b) -> Integer.compare(score(b.severity()), score(a.severity())));
        return findings;
    }

    private List<String> evidence(String original, DiagnosticRule rule) {
        return Arrays.stream(original.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> rule.matcher.test(line.toLowerCase(Locale.ROOT)))
                .limit(3)
                .map(line -> line.length() > 280 ? line.substring(0, 280) + "…" : line)
                .toList();
    }

    private List<String> firstNonBlankLines(String log, int count) {
        return Arrays.stream(log.split("\\R")).map(String::trim).filter(s -> !s.isBlank()).limit(count).toList();
    }

    private static boolean containsAny(String value, String... needles) {
        return Arrays.stream(needles).anyMatch(value::contains);
    }

    private static int score(String severity) {
        return switch (severity) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private record DiagnosticRule(
            String code, String title, String category, String severity, int confidence,
            Predicate<String> matcher, List<String> causes, List<String> suggestions
    ) {}
}

