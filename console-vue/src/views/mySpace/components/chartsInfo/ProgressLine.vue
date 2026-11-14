<!-- LinkStamp changes: display statistics as text without bundled brand images. -->
<template>
  <div class="main-box">
    <span v-if="!dataLists"> 所选日期内没有访问数据 </span>
    <div v-else>
      <div class="flex-box" v-for="(item, index) in dataLists" :key="index">
        <div class="flex-item">
          <div>
            <span>{{ item?.browser || item?.os }} {{ (item?.ratio * 100).toFixed(2) }}%</span>
          </div>
          <div>
            <span>{{ item?.cnt }} 次</span>
          </div>
        </div>
        <div>
          <el-progress
            color="#254bc3"
            :text-inside="true"
            :show-text="false"
            :stroke-width="12"
            :percentage="Number((item?.ratio * 100).toFixed(2))"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>

defineProps({
  dataLists: {
    type: Array
    // eslint-disable-next-line vue/require-valid-default-prop
    // default: [
    //   {
    //     name: 'windows',
    //     img: null,
    //     times: 2,
    //     percentage: 50
    //   },
    //   {
    //     name: 'linux',
    //     img: null,
    //     times: 2,
    //     percentage: 50
    //   },
    // ]
  }
})
</script>

<style lang="scss" scoped>
.main-box {
  padding: 20px;

  .flex-box {
    height: 50px;
    display: flex;
    flex-direction: column;

    .flex-item {
      height: 35px;
      display: flex;
      align-items: center;
      justify-content: space-between;

      span {
        font-size: 15px;
      }

    }
  }
}

:deep(.el-progress-bar__innerText) {
  font-size: 12px;
  transform: translateY(-1.5px) !important;
}
</style>
