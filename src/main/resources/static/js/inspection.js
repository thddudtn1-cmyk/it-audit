// 점검 입력: 점검결과가 정상이 아니면 조치내용·개선 예정일 칸을 강조 (외부 라이브러리 없음)
(function () {
    'use strict';
    var form = document.querySelector('[data-inspection-form]');
    if (!form) {
        return;
    }
    var box = form.querySelector('[data-action-fields]');
    function sync() {
        var checked = form.querySelector('input[name="inspectionResult"]:checked');
        var needsAction = checked && checked.value !== 'NORMAL';
        box.classList.toggle('action-fields--required', !!needsAction);
    }
    form.querySelectorAll('input[name="inspectionResult"]').forEach(function (r) {
        r.addEventListener('change', sync);
    });
    sync();
})();
