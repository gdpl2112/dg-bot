function formatMsgTime(timespan) {
    let dateTime = new Date(timespan)
    let year = dateTime.getFullYear()
    let month = dateTime.getMonth() + 1
    let day = dateTime.getDate()
    let hour = dateTime.getHours()
    let minute = dateTime.getMinutes()
    let millisecond = dateTime.getTime()
    let now = new Date()
    let nowNew = now.getTime()
    let milliseconds = 0
    let timeSpanStr
    milliseconds = nowNew - millisecond
    if (milliseconds <= 1000 * 60 * 1) {
        timeSpanStr = '刚刚'
    } else if (1000 * 60 < milliseconds && milliseconds <= 1000 * 60 * 60) {
        timeSpanStr = Math.round((milliseconds / (1000 * 60))) + '分钟前'
    } else if (1000 * 60 * 60 < milliseconds && milliseconds <= 1000 * 60 * 60 * 24) {
        timeSpanStr = Math.round(milliseconds / (1000 * 60 * 60)) + '小时前'
    } else if (1000 * 60 * 60 * 24 < milliseconds && milliseconds <= 1000 * 60 * 60 * 24 * 15) {
        timeSpanStr = Math.round(milliseconds / (1000 * 60 * 60 * 24)) + '天前'
    } else if (milliseconds > 1000 * 60 * 60 * 24 * 15 && year === now.getFullYear()) {
        timeSpanStr = month + '-' + day + ' ' + hour + ':' + minute
    } else {
        timeSpanStr = year + '-' + month + '-' + day + ' ' + hour + ':' + minute
    }
    return timeSpanStr
}

function formatMsgTime1(timespan, or) {
    if (timespan <= 0) return or;
    let dateTime = new Date(timespan)
    let millisecond = dateTime.getTime()
    let now = new Date()
    let nowNew = now.getTime()
    let milliseconds = 0
    let timeSpanStr
    milliseconds = nowNew - millisecond
    if (milliseconds <= 1000 * 60) {
        timeSpanStr = milliseconds + "秒"
    } else if (1000 * 60 < milliseconds && milliseconds <= 1000 * 60 * 60) {
        timeSpanStr = Math.round((milliseconds / (1000 * 60))) + '分钟'
            + Math.round((milliseconds % (1000 * 60)) / 1000) + "秒"
    } else if (1000 * 60 * 60 < milliseconds && milliseconds <= 1000 * 60 * 60 * 24) {
        timeSpanStr = Math.round(milliseconds / (1000 * 60 * 60)) + '小时'
            + Math.round((milliseconds % (1000 * 60 * 60)) / 1000) + '分钟'
    } else {
        timeSpanStr = Math.round(milliseconds / (1000 * 60 * 60 * 24)) + '天'
            + Math.round(milliseconds % (1000 * 60 * 60 * 24) / 1000) + '小时'
    }
    return timeSpanStr
}

function formatMsgTime0(timespan) {
    let dateTime = new Date(timespan)
    let day = dateTime.getDate()
    let hour = dateTime.getHours()
    let minute = dateTime.getMinutes()
    return day + "日" + hour + ":" + minute;
}

function getCookie(name) {
    let strcookie = document.cookie;
    let arrcookie = strcookie.split("; ");
    for (let i = 0; i < arrcookie.length; i++) {
        let arr = arrcookie[i].split("=");
        if (arr[0] == name) {
            return arr[1];
        }
    }
    return null;
}

/**
 * 添加cookie
 * @param name cookie名字
 * @param value 值
 */
function setCookie(name, value) {
    let Days = 30;
    let exp = new Date();
    exp.setTime(exp.getTime() + Days * 24 * 60 * 60 * 1000);
    document.cookie = name + "=" + escape(value) + ";expires=" + exp.toGMTString();
}

/**
 * @param {String} text 需要复制的内容
 * @return {Boolean} 复制成功:true或者复制失败:false  执行完函数后，按ctrl + v试试
 */
function copyText(text) {
    navigator.clipboard.writeText(copyText.value);
}