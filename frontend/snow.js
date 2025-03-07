// import jQuery from "jquery";

document.addEventListener('DOMContentLoaded', function() {
    let itSnow = document.querySelectorAll('.festive').length;
    if (itSnow) {
        const footer = document.querySelector('footer');
        const flakeDiameter = 6;
        let width = window.innerWidth - flakeDiameter;
        let height = footer.getBoundingClientRect().top + footer.offsetHeight - flakeDiameter;
        let wind = 0;

        function createSnowFlake() {
            const x = Math.random() * width;
            const y = Math.random() * height;
            const el = document.createElement('div');
            el.style.width = flakeDiameter + 'px';
            el.style.height = flakeDiameter + 'px';
            el.style.borderRadius = '4px';
            el.style.backgroundColor = '#f7f7f7';
            el.style.position = 'absolute';
            el.style.top = -y + 'px';
            el.style.left = x + 'px';
            el.style.boxShadow = '1px 1px 2px';
            el.style.zIndex = '5';
            document.body.appendChild(el);
            return { el, x, y };
        }

        function animate(flake) {
            wind = Math.max(-0.1, Math.min(0.1, wind + Math.random() * 0.001 - (0.001 / 2)));
            const newY = flake.y + (Math.random() * 2);
            let newX = flake.x + Math.random() - (0.5 + wind);

            if (newX < 0) {
                newX = width;
            } else if (newX > width + flakeDiameter) {
                newX = flakeDiameter;
            }
            if (newY > height) {
                flake.y = -10;
                flake.x = Math.random() * width;
            } else {
                flake.y = newY;
                flake.x = newX;
            }
            flake.el.style.top = flake.y + 'px';
            flake.el.style.left = flake.x + 'px';
        }

        const flakes = Array.from({ length: 100 }, createSnowFlake);

        window.addEventListener('resize', function() {
            const newWidth = window.innerWidth - flakeDiameter;
            const newHeight = footer.getBoundingClientRect().top + footer.offsetHeight - flakeDiameter;
            flakes.forEach(function(flake) {
                flake.x = (flake.x * (newWidth / width));
            });
            width = newWidth;
            height = newHeight;
        });

        function tick() {
            flakes.forEach(animate);
            requestAnimationFrame(tick);
        }
        tick();
    }
});

// jQuery(function($) {
//     let itSnow = $('.festive').length;
//     if(itSnow) {
//         const footer = $('footer');
//         const flakeDiameter = 6;
//         let width = $(window).width() - flakeDiameter;
//         let height = footer.offset().top + footer.outerHeight() - flakeDiameter;
//         let wind = 0;

//         function createSnowFlake() {
//             const x = Math.random() * width;
//             const y = Math.random() * height;
//             const el = $("<div style='width: " + flakeDiameter + "px; height: " + flakeDiameter + "px; border-radius: 4px; background-color: #f7f7f7; position: absolute; top: " + -y + "px; left: " + x + "px; box-shadow: 1px 1px 2px; z-index: 5;'></div>");
//             $(document.body)
//                 .append(el);
//             return {el, x, y};
//         }

//         function animate(flake) {
//             wind = Math.max(-0.1, Math.min(0.1), wind + Math.random() * 0.001 - (0.001 / 2));
//             const newY = flake.y + (Math.random() * 2);
//             let newX = flake.x + Math.random() - (0.5 + wind);

//             if (newX < 0) {
//                 newX = width;
//             } else if (newX > width + flakeDiameter) {
//                 newX = flakeDiameter;
//             }
//             if (newY > height) {
//                 flake.y = -10;
//                 flake.x = Math.random() * width;
//             } else {
//                 flake.y = newY;
//                 flake.x = newX;
//             }
//             flake.el.css("top", flake.y).css("left", flake.x);
//         }

//         const flakes = [...Array(100).keys()].map(function(){
//             return createSnowFlake();
//         });

//         $(window).on('resize', function(){
//             const newWidth = $(window).width() - flakeDiameter;
//             const newHeight = footer.offset().top + footer.outerHeight() - flakeDiameter;
//             flakes.map(function(flake) {
//                 flake.x = (flake.x * (newWidth / width));
//             });
//             width = newWidth;
//             height = newHeight;
//         });

//         function tick() {
//             flakes.forEach(animate);
//             requestAnimationFrame(tick);
//         }
//         tick();
//     }
// });
