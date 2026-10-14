// 체크리스트 관리: 행 선택 → 선택 삭제 (외부 라이브러리 없음)
(function () {
    'use strict';
    var form = document.getElementById('delete-form');
    if (!form) {
        return;
    }
    var button = document.getElementById('delete-button');
    var all = document.getElementById('check-all');
    var boxes = Array.prototype.slice.call(form.querySelectorAll('.row-check:not(:disabled)'));

    function sync() {
        var n = boxes.filter(function (b) { return b.checked; }).length;
        if (button) {
            button.disabled = n === 0;
            button.textContent = n === 0 ? '선택 삭제' : '선택 삭제 (' + n + '건)';
        }
        if (all) {
            all.checked = boxes.length > 0 && n === boxes.length;
            all.indeterminate = n > 0 && n < boxes.length;
            all.disabled = boxes.length === 0;
        }
    }

    if (all) {
        all.addEventListener('change', function () {
            boxes.forEach(function (b) { b.checked = all.checked; });
            sync();
        });
    }
    boxes.forEach(function (b) { b.addEventListener('change', sync); });

    form.addEventListener('submit', function (e) {
        var n = boxes.filter(function (b) { return b.checked; }).length;
        if (n === 0 || !window.confirm(n + '건을 삭제합니다.\n' + form.getAttribute('data-confirm'))) {
            e.preventDefault();
        }
    });
    sync();
})();
