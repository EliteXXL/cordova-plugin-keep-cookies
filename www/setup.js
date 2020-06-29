var exec = require('cordova/exec');

if (cordova.platformId !== "browser") {
    let METHOD = Symbol('method');
    let URL = Symbol('url');
    
    XMLHttpRequest.prototype.open = (function (open) {
        return function (method, url) {
            this[METHOD] = method;
            this[URL] = url;
            open.call(this, method, url);
        };
    })(XMLHttpRequest.prototype.open);
    XMLHttpRequest.prototype.send = (function (send) {
        return function (input) {
            if (this.readyState === this.OPENED) {
                exec(function () {}, function () {}, 'KeepCookies', 'storeRequestData', [ this[METHOD], this[URL], input ]);
            }
            send.call(this, input);
        };
    })(XMLHttpRequest.prototype.send);
}