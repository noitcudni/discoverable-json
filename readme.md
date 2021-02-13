# Discoverable Json Chrome Extension
Inspired by [gron](https://github.com/tomnomnom/gron)
Dicoverable Json Extension flattens your JSON data (however nested) into valid javascript expressions. It's a very simple but power concept. With absolute javascript paths, you can easiy search for the sliver of data you care about. Or, remove the data that are not relevant to you.

It automatically reconstructs your data manipulation back to valid JSON. You can also download the end result.

## Installation
1. Install Java.
2. Install [leiningen](http://leiningen.org).
3. `git clone git@github.com:noitcudni/cljs-gron-online.git`
4. `cd` into `cljs-gron-online`
* In your terminal
```bash
lein jar && lein install
```
5. `git clone git@github.com:noitcudni/discoverable-json.git`
6. `cd` into `discoverable-json`
* In your terminal
```bash
lein release && lein package
```
5. Go to **chrome://extensions/** and turn on Developer mode.
6. Click on **Load unpacked extension . . .** and load the extension.
