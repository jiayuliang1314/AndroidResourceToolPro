# 重命名lib资源名称+无用资源删除插件

参考开源项目：

https://github.com/zhaoyubetter/AndroidResourceTools

https://github.com/jpage4500/FindUnusedResources

解决其问题：
1.不支持最新gradle版本
2.中文编码问题


# AndroidResourceTools

因为模块化开发时，android 的资源名称（除id外）是不能重名的，如果手动修改，很繁琐；
本插件的目的：**重命名Android 资源名称与资源文件名；方便模块化开发；**


# 效果展示


![效果展示](https://github.com/zhaoyubetter/MarkdownPhotos/raw/master/img/plugin/res_demo.gif)

# 使用说明

1. 在项目的根目录`build.gradle`,添加依赖如下
```
    repositories {
	    ...
        mavenCentral()
    }
```

```java
dependencies {
	...
    classpath 'io.github.jiayuliang1314:ResTools:1.0.0'
}
```

2. 在对应的 module 下 `build.gradle`下，apply 插件，如下：

```java
apply plugin: 'com.android.application'
apply plugin: 'plugin.resTools'  // 资源重命名插件

// 配置插件dsl
resConfig {
    new_prefix = 'better_'  // 资源前缀
    old_prefix = ''         // 老前缀，可为''空字符串
    // === below use default
    // resFolderPath 资源目录
    // srcFolderPath 源代码目录
    // manifestFilePath 清单文件目录
}
```

3. clean 工程后，可以发现，在对应的 module 下，可发现 `resourcetools` task 组，展开，点击「replaceResName」 执行；

# 特别说明(特别重要)

>1. 因是直接替换文件，千万不要在主分支，主开发分支使用，建议使用新分支，测试ok后，合并；
>2. 目前插件没有加入文件事务处理，即：不能实现要么全部成功，要么全部失败；


## `1.0.1` 版本支持的资源如下：

通过分析Android工程，分析得出Android App 工程的资源分为2大类：

- 文件夹类型；
- values类型（**目前values类型，尚不支持 attrs 与 styles**）；

| 资源类型 | 源代码中 | xml引用中 |备注|
| :--- | ------ | :---- |:--|
| layout | R.layout.XXX<br />import kotlinx.android.synthetic.main.xxx.* | @layout/XXX |布局文件<br />kotlin导入布局时，重名支持|
| drawable | R.drawable.XXX | @drawable/XXX ||
| anim | R.anim.XXX | @anim/XXX ||
| color | R.color.XXX | @color/XXX ||
| mipmap | R.mipmap.XXX | @mipmap.XXX ||
| menu | R.menu.XXX |  |Xml 暂无引用|
| raw | R.raw.XXX |  |Xml 暂无引用|
| xml | R.xml.XXX |  |Xml 暂无引用|
|  |  |  ||
| string | R.string.XXX | @string/XXX ||
| color | R.color.XXX | @color/XXX ||
| dimens | R.dimen.XXX | @dimen/XXX ||
| arrays | R.array.XXX | @array/XXX ||
| style | R.style.XXX | @style/XXX |需要考虑到parent，比较复杂|
| attr | R.attr.XXX |  |需要特殊处理，主要在自定义属性上|


# TODO
1. 文件的事务操作有待支持；
2. Attrs 有待支持；
3. style有待支持；
4. 其他资源类型有待支持；
5. 如果资源名称与Android自带资源名重复时，会发生替换问题，此问题，有待修正；


# 无用资源删除插件
## 1.索引资源的id名
```
private static final Map<String, AtomicInteger> mStringMap = new TreeMap<>();
private static final Map<String, AtomicInteger> mDimenMap = new TreeMap<>();
private static final Map<String, AtomicInteger> mColorMap = new TreeMap<>();
private static final Map<String, AtomicInteger> mStringArrayMap = new TreeMap<>();
private static final Map<String, AtomicInteger> mDrawableMap = new TreeMap<>();
private static final Map<String, AtomicInteger> mLayoutMap = new TreeMap<>();
private static final Map<String, AtomicInteger> mStylesMap = new TreeMap<>();
```
## 2.遍历文件和AndroidManifest.xml，计算资源使用次数

## 3.删除无用资源

# TODO
1.索引的类型
2.现在是单个lib，整个项目待支持

重复上述步骤多次