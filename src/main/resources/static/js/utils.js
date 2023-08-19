
//序列化表单字段为json对象
$.fn.serializeFormToJson = function(){
    let arr = $(this).serializeArray();//form表单数据 name：value
    let param = {};
    $.each(arr,function(i,obj){ //将form表单数据封装成json对象
        param[obj.name] = obj.value;
    })
    return param;
}

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
    if (milliseconds <= 1000 * 60) {
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
    let t0 = timespan
    let now = new Date().getTime();
    let ms = now - t0;
    let day = 0;
    let hour = 0;
    let min = 0;
    let sec = 0;
    while (ms > 1000) {
        ms -= 1000;
        sec++;
        if (sec === 60) {
            min++;
            sec = 0;
            if (min === 60) {
                hour++;
                min = 0;
                if (hour === 24) {
                    day++;
                    hour = 0;
                }
            }
        }
    }
    return  day + "天" + hour + "小时" + min + "分钟" + sec + "秒";
}

function formatMsgTime0(timespan) {
    let dateTime = new Date(timespan)
    let day = dateTime.getDay()
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