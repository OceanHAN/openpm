<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="600">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="只有「多分支 / 多平台」产品才能建分支"
      description="禅道的分支是产品维度的一个字段：产品类型为 branch 叫「分支」，为 platform 叫「平台」，normal 类型的产品没有分支。id=0 的「主干」是虚拟行，不需要也不能创建。"
    />
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="110px">
      <el-form-item label="所属产品" prop="product">
        <el-select v-model="formData.product" placeholder="请选择产品" filterable class="w-full"
                   :disabled="formType === 'update'">
          <el-option v-for="p in branchEnabledProducts" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
        <div v-if="branchEnabledProducts.length === 0" class="text-12px text-red-500 mt-4px">
          还没有「多分支 / 多平台」类型的产品，请先到产品管理把产品类型改成 branch 或 platform
        </div>
      </el-form-item>
      <el-form-item :label="label + '名称'" prop="name">
        <el-input v-model="formData.name" :placeholder="'请输入' + label + '名称'" maxlength="255" />
      </el-form-item>
      <el-form-item label="描述" prop="desc">
        <el-input v-model="formData.desc" type="textarea" :rows="3" placeholder="请输入描述" maxlength="255" />
      </el-form-item>
      <el-form-item label="设为默认" prop="setDefault">
        <el-switch v-model="formData.setDefault" />
        <span class="ml-8px text-12px text-gray-500">默认分支会被计划/发布列表默认选中</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as BranchApi from '@/api/zentao/branch'
import * as ProductApi from '@/api/zentao/product'

defineOptions({ name: 'ZentaoBranchForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()
const productList = ref<ProductApi.ProductSimpleVO[]>([])

const formData = ref<BranchApi.BranchVO & { setDefault?: boolean }>({})

// 只有 branch / platform 类型的产品能建分支，normal 的选了也会被后端拒绝，这里直接过滤掉
const branchEnabledProducts = computed(() =>
  productList.value.filter((p) => p.type === 'branch' || p.type === 'platform')
)

// 文案随产品类型变化：产品是 platform 时叫「平台」
const label = computed(() => {
  const hit = productList.value.find((p) => p.id === formData.value.product)
  return hit?.type === 'platform' ? '平台' : '分支'
})

const formRules = reactive({
  product: [{ required: true, message: '所属产品不能为空', trigger: 'change' }],
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }]
})

const open = async (type: string, id?: number, productId?: number) => {
  dialogVisible.value = true
  formType.value = type
  dialogTitle.value = type === 'create' ? '新建分支/平台' : '编辑分支/平台'
  resetForm()
  productList.value = await ProductApi.getProductSimpleList()
  if (id) {
    formLoading.value = true
    try {
      const data = await BranchApi.getBranch(id)
      formData.value = { ...data, setDefault: data.defaultFlag === 1 }
    } finally {
      formLoading.value = false
    }
  } else if (productId) {
    formData.value.product = productId
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await BranchApi.createBranch(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await BranchApi.updateBranch(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = { product: undefined, name: '', desc: '', setDefault: false }
  formRef.value?.resetFields()
}
</script>
