// 적정성 검토: 선택한 판정에 해당하는 입력칸만 보여준다 (외부 라이브러리 없음)
(function () {
    'use strict';
    var form = document.querySelector('[data-review-form]');
    if (!form) {
        return;
    }
    var sections = form.querySelectorAll('[data-decision-section]');
    function sync() {
        var checked = form.querySelector('input[name="decision"]:checked');
        var value = checked ? checked.value : null;
        sections.forEach(function (s) {
            s.hidden = s.getAttribute('data-decision-section') !== value;
        });
    }
    form.querySelectorAll('input[name="decision"]').forEach(function (r) {
        r.addEventListener('change', sync);
    });
    sync();
})();
